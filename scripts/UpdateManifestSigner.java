import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dependency-free Java 17 signer for KinogoATV's updater envelope.
 *
 * Passwords are read only from the process environment. They are never accepted as command-line
 * arguments, printed, exported, or written to the manifest.
 */
public final class UpdateManifestSigner {
    private static final long MAX_APK_SIZE_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_MANIFEST_LIFETIME_SECONDS = 90L * 24L * 60L * 60L;
    private static final int MAX_DOWNLOAD_URLS = 4;
    private static final int MAX_URL_CHARS = 8 * 1024;
    private static final Pattern VERSION_NAME =
            Pattern.compile("\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.-]+)?");
    private static final Pattern IPV4_LITERAL = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");

    private UpdateManifestSigner() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length > 0 && "certificate-sha256".equals(args[0])) {
            printCertificateSha256(parseOptions(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        if (args.length == 0 || !"sign".equals(args[0])) {
            throw new IllegalArgumentException(
                    "Expected command: sign, certificate-sha256, or self-test"
            );
        }
        sign(parseOptions(Arrays.copyOfRange(args, 1, args.length)));
    }

    private static void printCertificateSha256(Options options) throws Exception {
        Path keyStorePath = requiredPath(options, "--keystore").toAbsolutePath().normalize();
        String alias = requiredValue(options, "--alias");
        require(Files.isRegularFile(keyStorePath), "Signing keystore does not exist");
        char[] storePassword = requiredSecret("KINOGO_SIGNING_STORE_PASSWORD");
        char[] keyPassword = optionalSecret("KINOGO_SIGNING_KEY_PASSWORD", storePassword);
        try {
            SigningIdentity identity = loadSigningIdentity(
                    keyStorePath,
                    alias,
                    storePassword,
                    keyPassword
            );
            System.out.println(
                    HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(identity.certificate.getEncoded())
                    )
            );
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }

    private static void sign(Options options) throws Exception {
        Path apk = requiredPath(options, "--apk").toAbsolutePath().normalize();
        Path keyStorePath = requiredPath(options, "--keystore").toAbsolutePath().normalize();
        Path output = requiredPath(options, "--output").toAbsolutePath().normalize();
        String alias = requiredValue(options, "--alias");
        String expectedCertificateSha256 = requiredValue(
                options,
                "--expected-certificate-sha256"
        ).toLowerCase(Locale.ROOT);
        String versionName = requiredValue(options, "--version-name");
        long versionCode = positiveLong(requiredValue(options, "--version-code"), "version code");
        long issuedAt = options.singleValues.containsKey("--issued-at")
                ? positiveLong(options.singleValues.get("--issued-at"), "issued-at")
                : Instant.now().getEpochSecond();
        long expiresAt = positiveLong(requiredValue(options, "--expires-at"), "expires-at");
        boolean dryRun = options.flags.contains("--dry-run");

        require(Files.isRegularFile(apk), "APK does not exist");
        require(Files.isRegularFile(keyStorePath), "Signing keystore does not exist");
        require(!apk.equals(keyStorePath) && !output.equals(apk) && !output.equals(keyStorePath),
                "Input and output paths must be different");
        require(VERSION_NAME.matcher(versionName).matches(), "Version name is invalid");
        require(expectedCertificateSha256.matches("[0-9a-f]{64}"),
                "Expected signing certificate digest is invalid");
        require(expiresAt > issuedAt, "Manifest expiry must follow its issue time");
        require(expiresAt - issuedAt <= MAX_MANIFEST_LIFETIME_SECONDS,
                "Manifest lifetime exceeds 90 days");
        require(issuedAt <= Instant.now().getEpochSecond() + 60L,
                "Manifest issue time is unexpectedly in the future");

        String assetName = "KinogoATV-" + versionName + "-code" + versionCode + ".apk";
        require(apk.getFileName().toString().equals(assetName),
                "APK filename does not match version name/code");
        long assetSize = Files.size(apk);
        require(assetSize >= 1L && assetSize <= MAX_APK_SIZE_BYTES,
                "APK size is outside the updater limit");
        String sha256 = sha256(apk);

        List<String> downloadUrls = options.multiValues.getOrDefault("--download-url", List.of());
        require(downloadUrls.size() >= 1 && downloadUrls.size() <= MAX_DOWNLOAD_URLS,
                "One to four download URLs are required");
        require(new HashSet<>(downloadUrls).size() == downloadUrls.size(),
                "Download URLs must be unique");
        for (String url : downloadUrls) {
            validateDownloadUrl(url, assetName);
        }

        char[] storePassword = requiredSecret("KINOGO_SIGNING_STORE_PASSWORD");
        char[] keyPassword = optionalSecret("KINOGO_SIGNING_KEY_PASSWORD", storePassword);
        try {
            SigningIdentity identity = loadSigningIdentity(
                    keyStorePath,
                    alias,
                    storePassword,
                    keyPassword
            );
            String actualCertificateSha256 = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(identity.certificate.getEncoded())
            );
            require(actualCertificateSha256.equals(expectedCertificateSha256),
                    "APK signer does not match the manifest signing identity");
            String payload = buildPayload(
                    versionName,
                    versionCode,
                    assetName,
                    assetSize,
                    sha256,
                    issuedAt,
                    expiresAt,
                    downloadUrls
            );
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            byte[] signature = sign(identity.privateKey, payloadBytes);
            require(verify(identity.publicKey, payloadBytes, signature),
                    "Generated signature did not verify");

            String envelope = "{\"schema\":1,\"payload\":\""
                    + Base64.getEncoder().encodeToString(payloadBytes)
                    + "\",\"signature\":\""
                    + Base64.getEncoder().encodeToString(signature)
                    + "\"}\n";
            if (!dryRun) {
                writeAtomically(output, envelope.getBytes(StandardCharsets.UTF_8));
                System.out.println("Signed update manifest written: " + output);
            } else {
                System.out.println("Signed update manifest dry-run passed; no file was written");
            }
            System.out.println("Validated artifact: " + assetName + " (" + assetSize + " bytes)");
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }

    private static Options parseOptions(String[] args) {
        Map<String, String> singles = new HashMap<>();
        Map<String, List<String>> multiples = new HashMap<>();
        Set<String> flags = new HashSet<>();
        Set<String> singleNames = Set.of(
                "--apk",
                "--keystore",
                "--alias",
                "--expected-certificate-sha256",
                "--version-name",
                "--version-code",
                "--issued-at",
                "--expires-at",
                "--output"
        );
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--dry-run".equals(argument)) {
                require(flags.add(argument), "Duplicate flag: " + argument);
                continue;
            }
            require(index + 1 < args.length, "Missing value for " + argument);
            String value = args[++index];
            require(!value.isBlank(), "Empty value for " + argument);
            if ("--download-url".equals(argument)) {
                multiples.computeIfAbsent(argument, unused -> new ArrayList<>()).add(value);
            } else {
                require(singleNames.contains(argument), "Unknown option: " + argument);
                require(singles.putIfAbsent(argument, value) == null,
                        "Duplicate option: " + argument);
            }
        }
        return new Options(singles, multiples, flags);
    }

    private static Path requiredPath(Options options, String name) {
        return Path.of(requiredValue(options, name));
    }

    private static String requiredValue(Options options, String name) {
        String value = options.singleValues.get(name);
        require(value != null && !value.isBlank(), "Missing option: " + name);
        return value;
    }

    private static long positiveLong(String raw, String label) {
        require(raw.matches("[1-9]\\d*"), "Invalid " + label);
        try {
            long value = Long.parseLong(raw);
            require(value > 0L, "Invalid " + label);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + label);
        }
    }

    private static char[] requiredSecret(String name) {
        String raw = System.getenv(name);
        require(raw != null && !raw.isEmpty(), "Required signing password is unavailable");
        return raw.toCharArray();
    }

    private static char[] optionalSecret(String name, char[] fallback) {
        String raw = System.getenv(name);
        return raw == null || raw.isEmpty() ? fallback.clone() : raw.toCharArray();
    }

    private static SigningIdentity loadSigningIdentity(
            Path path,
            String alias,
            char[] storePassword,
            char[] keyPassword
    ) {
        for (String type : List.of("PKCS12", "JKS")) {
            try (InputStream input = Files.newInputStream(path)) {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(input, storePassword);
                Key key = keyStore.getKey(alias, keyPassword);
                Certificate certificate = keyStore.getCertificate(alias);
                if (key instanceof PrivateKey && certificate != null) {
                    PrivateKey privateKey = (PrivateKey) key;
                    PublicKey publicKey = certificate.getPublicKey();
                    require(algorithm(privateKey).equals(algorithm(publicKey)),
                            "Signing key and certificate do not match");
                    return new SigningIdentity(privateKey, publicKey, certificate);
                }
            } catch (Exception ignored) {
                // Try the other standard Java keystore container. Never expose provider errors.
            }
        }
        throw new IllegalArgumentException("Unable to load the signing identity from the keystore");
    }

    private static byte[] sign(PrivateKey privateKey, byte[] payload) throws Exception {
        Signature signer = Signature.getInstance(signatureAlgorithm(privateKey));
        signer.initSign(privateKey);
        signer.update(payload);
        return signer.sign();
    }

    private static boolean verify(PublicKey publicKey, byte[] payload, byte[] signature)
            throws Exception {
        Signature verifier = Signature.getInstance(signatureAlgorithm(publicKey));
        verifier.initVerify(publicKey);
        verifier.update(payload);
        return verifier.verify(signature);
    }

    private static String signatureAlgorithm(Key key) {
        return switch (algorithm(key)) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            default -> throw new IllegalArgumentException("Unsupported signing key algorithm");
        };
    }

    private static String algorithm(Key key) {
        return key.getAlgorithm().toUpperCase(Locale.ROOT);
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void validateDownloadUrl(String rawUrl, String assetName) {
        require(rawUrl.length() >= 1 && rawUrl.length() <= MAX_URL_CHARS,
                "Download URL is invalid");
        require(rawUrl.chars().noneMatch(character ->
                        Character.isISOControl(character) || character == '\\'),
                "Download URL is invalid");
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Download URL is invalid");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        require("https".equalsIgnoreCase(uri.getScheme())
                        && uri.getRawUserInfo() == null
                        && uri.getPort() == -1
                        && uri.getRawFragment() == null
                        && path != null
                        && path.startsWith("/")
                        && path.endsWith("/" + assetName)
                        && host.contains(".")
                        && !"localhost".equals(host)
                        && !IPV4_LITERAL.matcher(host).matches()
                        && !host.contains(":"),
                "Download URL is not an allowed public HTTPS artifact address");
    }

    private static String buildPayload(
            String versionName,
            long versionCode,
            String assetName,
            long assetSize,
            String sha256,
            long issuedAt,
            long expiresAt,
            List<String> downloadUrls
    ) {
        StringBuilder urls = new StringBuilder("[");
        for (int index = 0; index < downloadUrls.size(); index++) {
            if (index > 0) {
                urls.append(',');
            }
            urls.append('"').append(jsonEscape(downloadUrls.get(index))).append('"');
        }
        urls.append(']');
        return "{"
                + "\"versionName\":\"" + jsonEscape(versionName) + "\","
                + "\"versionCode\":" + versionCode + ","
                + "\"assetName\":\"" + jsonEscape(assetName) + "\","
                + "\"assetSizeBytes\":" + assetSize + ","
                + "\"sha256\":\"" + sha256 + "\","
                + "\"issuedAtEpochSeconds\":" + issuedAt + ","
                + "\"expiresAtEpochSeconds\":" + expiresAt + ","
                + "\"downloadUrls\":" + urls
                + "}";
    }

    private static String jsonEscape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    private static void writeAtomically(Path output, byte[] bytes) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, ".manifest-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(
                        temporary,
                        output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void selfTest() throws Exception {
        byte[] payload = "{\"selfTest\":true}".getBytes(StandardCharsets.UTF_8);
        for (String algorithm : List.of("RSA", "EC")) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
            generator.initialize("RSA".equals(algorithm) ? 2048 : 256);
            KeyPair pair = generator.generateKeyPair();
            byte[] signature = sign(pair.getPrivate(), payload);
            require(verify(pair.getPublic(), payload, signature),
                    algorithm + " self-test signature failed");
            byte[] changed = payload.clone();
            changed[changed.length - 1] ^= 1;
            require(!verify(pair.getPublic(), changed, signature),
                    algorithm + " self-test accepted a changed payload");
        }
        System.out.println("UpdateManifestSigner self-test passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record Options(
            Map<String, String> singleValues,
            Map<String, List<String>> multiValues,
            Set<String> flags
    ) {}

    private record SigningIdentity(
            PrivateKey privateKey,
            PublicKey publicKey,
            Certificate certificate
    ) {}
}

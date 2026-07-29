package com.kinogo.atv.data.catalog

import java.io.IOException

sealed class CatalogException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class CatalogNetworkException(cause: Throwable) :
    CatalogException("Не удалось загрузить каталог", cause)

class CatalogHttpStatusException(val statusCode: Int) :
    CatalogException("Источник ответил HTTP $statusCode")

class CatalogChallengeException :
    CatalogException("Источник требует проверку в браузере")

class CatalogFingerprintException :
    CatalogException("Страница не похожа на поддерживаемую версию Kinogo")

class CatalogRedirectException(val resolvedOrigin: String) :
    CatalogException("Источник перенаправил запрос на непроверенный адрес $resolvedOrigin")

class CatalogResponseTooLargeException(val limitBytes: Int) :
    CatalogException("HTML-страница превышает лимит $limitBytes байт")

class CatalogContentTypeException(val contentType: String?) :
    CatalogException(
        "Источник вернул неподдерживаемый Content-Type: " +
            (contentType?.takeIf(String::isNotBlank) ?: "не указан"),
    )

class CatalogCharsetException(val charsetName: String) :
    CatalogException(
        "Источник указал неподдерживаемую кодировку: " +
            charsetName.ifBlank { "пустое значение" },
    )

class CatalogParseException(message: String, cause: Throwable? = null) :
    CatalogException(message, cause)

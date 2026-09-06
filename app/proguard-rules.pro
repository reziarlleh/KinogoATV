# Project-specific keep rules belong here only when a runtime/reflection boundary requires them.
# The current app uses explicit parsers and dependency-provided consumer rules, so release builds
# intentionally keep this file empty and let R8 remove unreachable code.

package digital.heirlooms.server.crypto.tlock

class TimeLockDecryptionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class TimeLockValidationException(message: String) : RuntimeException(message)

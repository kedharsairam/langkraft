package com.kedharsairam.langkraft.domain

// Result pattern (Kraft): data layer never throws to UI. UI only handles Success/Error.
sealed interface DomainResult<out T> {
    data class Success<out T>(val data: T) : DomainResult<T>
    data class Error(val reason: ErrorReason) : DomainResult<Nothing>
}

sealed interface ErrorReason {
    data object MissingAsset : ErrorReason
    data class MalformedJson(val detail: String) : ErrorReason
    data class Empty(val what: String) : ErrorReason
    data class Unknown(val detail: String) : ErrorReason
}

fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Success)?.data
fun <T> DomainResult<T>.fold(onSuccess: (T) -> Unit, onError: (ErrorReason) -> Unit) {
    when (this) {
        is DomainResult.Success -> onSuccess(data)
        is DomainResult.Error -> onError(reason)
    }
}

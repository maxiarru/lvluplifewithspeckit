package lvluplife.ui

import lvluplife.application.OperationRef
import lvluplife.data.ApiFailureCode

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Confirmed<T>(val value: T) : LoadState<T>
    data class Stale<T>(val value: T, val reason: ApiFailureCode) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val code: ApiFailureCode) : LoadState<Nothing>
    data class Pending(val operation: OperationRef) : LoadState<Nothing>
}

package me.robbin.mvvmscaffold.base.viewmodel

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.robbin.mvvmscaffold.callback.livedata.event.EventLiveData
import me.robbin.mvvmscaffold.network.ExceptionHandle
import me.robbin.mvvmscaffold.network.IBaseResponse
import me.robbin.mvvmscaffold.network.ResponseThrowable

/**
 * ViewModel基类
 * Create by Robbin at 2020/6/30
 */
open class BaseViewModel() : ViewModel(), LifecycleObserver {

    val uiEvent: UIChange by lazy { UIChange() }

    private fun launchUI(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch { block() }

    /**
     * 启动冷数据流Flow
     * Create by Robbin at 2020/7/3
     */
    fun <T> launchFlow(block: suspend () -> T): Flow<T> {
        return flow {
            emit(block())
        }
    }

    /**
     * 不过滤请求结果
     * @param block          协程执行体
     * @param error          失败回调
     * @param complete       完成回调（无论成功失败都会调用）
     * @param isShowLoading  是否显示加载
     * Create by Robbin at 2020/7/3
     */
    fun launchGo(
        block: suspend CoroutineScope.() -> Unit,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit = {
            uiEvent.toastEvent.postValue("${it.code}: ${it.errMsg}")
        },
        complete: suspend CoroutineScope.() -> Unit = {},
        isShowLoading: Boolean = true
    ) {
        if (isShowLoading) uiEvent.showLoading.call()
        launchUI {
            handleException(
                withContext(Dispatchers.IO) { block },
                { error(it) },
                {
                    uiEvent.dismissLoading.call()
                    complete()
                }
            )
        }
    }

    /**
     * 过滤请求结果，其他全抛异常
     * @param block         协程执行体
     * @param success       成功回调
     * @param error         失败回调
     * @param complete      完成回调
     * @param isShowLoading  是否显示加载
     * Create by Robbin at 2020/7/3
     */
    fun <T> launchOnlyResult(
        block: suspend CoroutineScope.() -> IBaseResponse<T>,
        success: (T) -> Unit,
        error: (ResponseThrowable) -> Unit = {
            uiEvent.toastEvent.postValue("${it.code}: ${it.message}")
        },
        complete: () -> Unit = {},
        isShowLoading: Boolean = true
    ) {
        if (isShowLoading) uiEvent.showLoading.call()
        launchUI {
            handleException(
                { withContext(Dispatchers.IO) { block() } },
                { res ->
                    handleResponse(res) {
                        success(it)
                    }
                },
                { error(it) },
                {
                    uiEvent.dismissLoading.call()
                    complete()
                }
            )
        }
    }

    /**
     * 处理请求结果
     * @param response  请求结果
     * @param success   成功回调
     * Create by Robbin at 2020/7/3
     */
    private suspend fun <T> handleResponse(
        response: IBaseResponse<T>,
        success: suspend CoroutineScope.(T) -> Unit
    ) {
        coroutineScope {
            if (response.isSuccess()) success(response.data())
            else throw ResponseThrowable(response.code(), response.msg())
        }
    }

    /**
     * 异常统一处理
     * @param block     协程执行体
     * @param success   成功回调
     * @param error     失败回调
     * @param complete  完成回调
     * Create by Robbin at 2020/7/3
     */
    private suspend fun <T> handleException(
        block: suspend CoroutineScope.() -> IBaseResponse<T>,
        success: suspend CoroutineScope.(IBaseResponse<T>) -> Unit,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit,
        complete: suspend CoroutineScope.() -> Unit
    ) {
        coroutineScope {
            try {
                success(block())
            } catch (e: Throwable) {
                error(ExceptionHandle.handleException(e))
            } finally {
                complete()
            }
        }
    }

    /**
     * 异常统一处理
     * @param block     协程执行体
     * @param error     失败回调
     * @param complete  完成回调
     * Create by Robbin at 2020/7/3
     */
    private suspend fun handleException(
        block: suspend CoroutineScope.() -> Unit,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit,
        complete: suspend CoroutineScope.() -> Unit
    ) {
        coroutineScope {
            try {
                block()
            } catch (e: Throwable) {
                error(ExceptionHandle.handleException(e))
            } finally {
                complete()
            }
        }
    }

    /**
     * 与网络加载配套使用
     */
    inner class UIChange {
        // 开始加载动画
        val showLoading by lazy { EventLiveData<String>() }

        // 结束加载动画
        val dismissLoading by lazy { EventLiveData<String>() }

        // 输出错误信息
        val toastEvent by lazy { EventLiveData<String>() }
    }

}
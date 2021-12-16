package com.yl.lib.sentry.hook

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.yl.lib.sentry.hook.hook.BaseHookBuilder
import com.yl.lib.sentry.hook.hook.BaseHooker
import com.yl.lib.sentry.hook.hook.ams.AmsHooker
import com.yl.lib.sentry.hook.hook.cms.CmsHooker
import com.yl.lib.sentry.hook.hook.pms.PmsHooker
import com.yl.lib.sentry.hook.hook.tms.TmsHooker
import com.yl.lib.sentry.hook.printer.BaseFilePrinter
import com.yl.lib.sentry.hook.printer.BasePrinter
import com.yl.lib.sentry.hook.printer.DefaultFilePrint
import com.yl.lib.sentry.hook.printer.PrintCallBack
import com.yl.lib.sentry.hook.util.PrivacyLog
import com.yl.lib.sentry.hook.util.PrivacyUtil
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author yulun
 * @sinice 2021-09-24 14:33
 */
class PrivacySentry {
    object Privacy {
        private var mBuilder: PrivacySentryBuilder? = null
        private val bInit = AtomicBoolean(false)
        private val bfinish = AtomicBoolean(false)
        var bShowPrivacy = false
        private var ctx: Application? = null

        fun init(ctx: Application) {
            init(ctx, null)
        }

        /**
         *  builder 自定义配置
         */
        fun init(
            ctx: Application, builder: PrivacySentryBuilder?
        ) {
            if (bInit.compareAndSet(false, true)) {
                if (builder == null) {
                    mBuilder = PrivacySentryBuilder().addPrinter(defaultFilePrinter(ctx, mBuilder))
                    mBuilder = defaultConfigHookBuilder(mBuilder!!)
                } else {
                    mBuilder = builder
                }
                initInner(ctx)
            }
        }

        private fun initInner(ctx: Application) {
            PrivacyLog.i("call initInner")
            this.ctx = ctx
            mBuilder?.getHookerList()?.forEach {
                it.hook(ctx)
            }
            mBuilder?.getWatchTime()?.let {
                PrivacyLog.i("delay stop watch $it")
                var handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    stopWatch()
                }, it)
            }
            mBuilder?.addPrinter(defaultFilePrinter(ctx, mBuilder))
        }

        fun stopWatch() {
            if (bfinish.compareAndSet(false, true)) {
                bfinish.set(true)
                PrivacyLog.i("call stopWatch")
                mBuilder?.getHookerList()?.forEach {
                    it.reduction(ctx!!)
                }
                mBuilder?.getPrinterList()?.filterIsInstance<BaseFilePrinter>()?.forEach {
                    it.flushToFile()
                    mBuilder?.getResultCallBack()?.onResultCallBack(it.resultFileName)
                }
            }

        }

        /**
         * 记录展示隐私协议，调用时机一般为 隐私协议点击确定的时候，必须调用
         */
        fun updatePrivacyShow() {
            if (bShowPrivacy) {
                return
            }
            PrivacyLog.i("call updatePrivacyShow")
            bShowPrivacy = true
            mBuilder?.getPrinterList()?.filterIsInstance<BaseFilePrinter>()
                ?.forEach { it.appendData("点击隐私协议确认", "点击隐私协议确认", "点击隐私协议确认") }
        }

        fun hasShowPrivacy(): Boolean {
            return bShowPrivacy
        }

        fun isDebug(): Boolean {
            return mBuilder?.debug ?: true
        }

        fun getContext(): Application? {
            return ctx ?: null
        }

        fun getBuilder(): PrivacySentryBuilder? {
            return mBuilder ?: null
        }

        fun defaultConfigHookBuilder(builder: PrivacySentryBuilder): PrivacySentryBuilder {
            builder?.configHook(defaultAmsHook(builder!!))
                ?.configHook(defaultPmsHook(builder!!))
                ?.configHook(defaultTmsHook(builder!!))
                ?.configHook(defaultCmsHook(builder!!))
                ?.syncDebug(true)
            return builder
        }

        private fun defaultFilePrinter(
            ctx: Context,
            builder: PrivacySentryBuilder?
        ): List<BasePrinter> {
            var fileName = builder?.getResultFileName() ?: "privacy_result_${
                PrivacyUtil.Util.formatTime(
                    System.currentTimeMillis()
                )
            }"
            PrivacyLog.i("print fileName is $fileName")
            return listOf(
                DefaultFilePrint(
                    "${ctx.getExternalFilesDir(null)}${File.separator}privacy${File.separator}$fileName.xls",
                    printCallBack = object : PrintCallBack {
                        override fun checkPrivacyShow(): Boolean {
                            return hasShowPrivacy()
                        }

                        override fun stopWatch() {
                            PrivacyLog.i("stopWatch")
                            Privacy.stopWatch()
                        }
                    }, watchTime = builder?.getWatchTime()
                )
            )
        }

        fun defaultAmsHook(mBuilder: PrivacySentryBuilder): BaseHooker {
            return AmsHooker(BaseHookBuilder("ams", mBuilder.getPrinterList()))
        }

        fun defaultTmsHook(mBuilder: PrivacySentryBuilder): BaseHooker {
            return TmsHooker(BaseHookBuilder("tms", mBuilder.getPrinterList()))
        }

        fun defaultPmsHook(mBuilder: PrivacySentryBuilder): BaseHooker {
            return PmsHooker(BaseHookBuilder("pms", mBuilder.getPrinterList()))
        }

        fun defaultCmsHook(mBuilder: PrivacySentryBuilder): BaseHooker {
            return CmsHooker(BaseHookBuilder("cms", mBuilder.getPrinterList()))
        }
    }
}

/**
 * 检测结果回调，业务方自行处理
 */
public interface PrivacyResultCallBack {
    fun onResultCallBack(filePath: String)
}
package com.hyper.market

import android.content.Context
import androidx.core.content.edit

object LaunchDialogHelper {
    private const val PREFERENCES = "launch_dialog_pref"
    private const val KEY_SHOWN = "first_launch_dialog_shown"

    fun shouldShow(context: Context): Boolean {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return !preferences.getBoolean(KEY_SHOWN, false)
    }

    fun markShown(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_SHOWN, true) }
    }

    internal val message = """
首先，谨向软件的原作者致以诚挚的歉意——因多方尝试未能与您取得联系，我不得已对软件进行了逆向修改，以修复首页及搜索功能出现的异常问题。

在此郑重声明：本修改版始终完全免费，任何付费行为均属倒卖，与本版无关。本版不含任何恶意代码，技术能力允许的朋友可自行逆向分析，程序未加壳，代码清晰可查。

如涉及侵权，将立即删除。如有任何问题，可通过 Telegram 联系我：@ibai_cn。

此外，未经本人明确允许，禁止将本修改版本进行二次传播或转载，敬请理解与配合。

感谢大家的支持。
""".trimIndent()
}

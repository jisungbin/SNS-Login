package com.sungbin.sungstarbook.kakao

import android.app.Activity
import android.content.Context
import com.kakao.auth.IApplicationConfig
import com.kakao.auth.ApprovalType
import com.kakao.auth.AuthType
import com.kakao.auth.ISessionConfig
import com.kakao.auth.KakaoAdapter
import com.sungbin.sungstarbook.GlobalApplication


class KakaoSDKAdapter : KakaoAdapter() {

    override fun getSessionConfig(): ISessionConfig {
        return object : ISessionConfig {
            override fun getAuthTypes(): Array<AuthType> {
                return arrayOf(AuthType.KAKAO_ACCOUNT)
            }

            override fun isUsingWebviewTimer(): Boolean {
                return false
            }


            override fun getApprovalType(): ApprovalType {
                return ApprovalType.INDIVIDUAL
            }

            override fun isSaveFormData(): Boolean {
                return true
            }
        }
    }

    override fun getApplicationConfig(): IApplicationConfig {
        return object : IApplicationConfig {
            override fun getTopActivity(): Activity {
                return GlobalApplication.getCurrentActivity()!!
            }

            override fun getApplicationContext(): Context {
                return GlobalApplication.getGlobalApplicationContext()!!
            }
        }
    }
}
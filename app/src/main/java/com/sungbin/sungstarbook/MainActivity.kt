package com.sungbin.sungstarbook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.facebook.*
import com.facebook.appevents.AppEventsLogger
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.kakao.auth.ErrorCode
import com.kakao.auth.ISessionCallback
import com.kakao.auth.Session
import com.kakao.network.ErrorResult
import com.kakao.usermgmt.UserManagement
import com.kakao.usermgmt.callback.MeResponseCallback
import com.kakao.usermgmt.response.model.UserProfile
import com.kakao.util.exception.KakaoException
import com.nhn.android.naverlogin.OAuthLogin
import com.nhn.android.naverlogin.OAuthLoginHandler
import com.shashank.sony.fancytoastlib.FancyToast
import com.sungbin.sungstarbook.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

    lateinit var googleLoginClient: GoogleApiClient
    private val RC_SIGN_IN = 1000
    private var mAuth: FirebaseAuth? = null
    var mOAuthLoginModule: OAuthLogin? = null
    private lateinit var snsLoginCallBack: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    private lateinit var snsLoginCallBackManager: CallbackManager


    private val mOAuthLoginHandler = @SuppressLint("HandlerLeak")
    object : OAuthLoginHandler() {
        override fun run(success: Boolean) {
            if (success) {
                Utils.toast(applicationContext,
                    "네이버 로그인 성공",
                    FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
            } else {
                val errorCode = mOAuthLoginModule!!.getLastErrorCode(applicationContext).code
                Utils.error(applicationContext, "네이버 로그인 실패\n에러 코드 : $errorCode")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FacebookSdk.sdkInitialize(applicationContext)
        AppEventsLogger.activateApp(this)

        /* ----- Firebase Auth ----- */
        mAuth = FirebaseAuth.getInstance()
        mAuth!!.setLanguageCode("kr")
        /* ---------- */

        /* ----- 익명 로그인 -----*/
        mAuth!!.signInAnonymously()
            .addOnCompleteListener(this) {
                if (it.isSuccessful) {
                    Utils.toast(applicationContext,
                        "익명 로그인에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                } else {
                   Utils.error(applicationContext,
                       "익명 로그인 과정에서 오류가 발생했습니다.\n${it.exception}")
                }
            }
        /* ---------- */

        /* ----- 네이버 로그인 ----- */
        mOAuthLoginModule = OAuthLogin.getInstance()
        mOAuthLoginModule!!.init(
            this,
            getString(R.string.naver_login_client_id),
            getString(R.string.naver_login_client_secret),
            getString(R.string.naver_login_client_name)
        )
        /* ---------- */

        /* ----- 카카오톡 로그인 ----- */
        Session.getCurrentSession().addCallback(KakaoCallBack(applicationContext))
        /* ---------- */

        /* ----- 구글 로그인 ----- */
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.google_login_client_id))
            .requestEmail()
            .build()

        googleLoginClient = GoogleApiClient.Builder(this)
            .enableAutoManage(this, this)
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
            .build()
        /* ---------- */

        /* ----- 전화번호 인증 ----- */
        snsLoginCallBackManager = CallbackManager.Factory.create()

        snsLoginCallBack = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Utils.error(applicationContext, "전화번호 인증에서 오류가 발생했습니다.\n$e")
            }

            override fun onCodeSent(
                verificationId: String?,
                token: PhoneAuthProvider.ForceResendingToken) {
                Log.d("CODE", "onCodeSent:" + verificationId!!) //전화번호로 전송된 인증번호
            }
        }
        /* ---------- */

        /* ------ 페이스북 로그인 ----- */
        LoginManager.getInstance().registerCallback(snsLoginCallBackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(loginResult: LoginResult) {
                    firebaseAuthWithFacebook(loginResult.accessToken)
                }

                override fun onCancel() {
                }

                override fun onError(e: FacebookException) {
                    Utils.error(applicationContext,
                        "페이스북 로그인에서 오류가 발생했습니다.\n$e")
                }
            })
        /* ---------- */

        /* ----- 로그인 버튼 등록 ----- */
        Google_Login.setOnClickListener {
            val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleLoginClient)
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        Sns_Login.setOnClickListener {
            phoneNumberVerification("국가번호를 포함한 전화번호 (ex: +8210123456789")
        }

        Naver_Login.setOAuthLoginHandler(mOAuthLoginHandler)

        Facebook_Login.setReadPermissions("email") //페이스북 로그인 요청할 정보 등록
        /* ---------- */

    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) { //Google Login
           val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result.isSuccess) {
                val account = result.signInAccount
                firebaseAuthWithGoogle(account!!)
            }
            else {

            }
        }
        else //전화번호 인증
            snsLoginCallBackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) { //구글 로그인 파베에 등록
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        mAuth!!.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Utils.toast(applicationContext,
                        "구글 로그인 Firebase Auth 처리에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                } else {
                    Utils.error(applicationContext,
                        "구글 로그인 Firebase Auth 처리에 실패했습니다.\n${task.exception}")
                }
            }
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) { //뭐였지?

    }

    private fun firebaseAuthWithFacebook(token: AccessToken) { //페북 로그인 파베에 등록
        val credential = FacebookAuthProvider.getCredential(token.token)
        mAuth!!.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Utils.toast(applicationContext,
                        "페이스북 로그인 Firebase Auth 처리에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                }
                else {
                    Utils.error(applicationContext,
                        "페이스북 로그인 Firebase Auth 처리에 실패했습니다.\n${task.exception}")
                }
            }
    }

    private fun phoneNumberVerification(phoneNumber: String) { //전화번호로 인증번호 발송
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber,
            60,
            TimeUnit.SECONDS,
            this,
            snsLoginCallBack)
    }

    private fun verifyPhoneNumberWithCode(verificationId: String?, code: String) { //전화번호로 전송된 인증번호 확인
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }


    private fun resendVerificationCode(phoneNumber: String,
            /*인증번호 코드 재발송 */   token: PhoneAuthProvider.ForceResendingToken?) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber,
            60,
            TimeUnit.SECONDS,
            this,
            snsLoginCallBack,
            token)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) { //인증번호를 통한 로그인
        mAuth!!.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Utils.toast(applicationContext,
                        "인증번호를 통한 로그인에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                }
                else {
                    Utils.error(applicationContext,
                        "인증번호를 통한 로그인에 실패했습니다.\n${task.exception}")
                }
            }
    }

    private class KakaoCallBack(applicationContext: Context) : ISessionCallback { //카카오톡 로그인 콜백

        var ctx: Context = applicationContext

        override fun onSessionOpened() {
            UserManagement.requestMe(object : MeResponseCallback() {
                override fun onFailure(errorResult: ErrorResult?) {
                    val result = ErrorCode.valueOf(errorResult!!.errorCode)
                    if (result == ErrorCode.CLIENT_ERROR_CODE) {
                        Utils.error(ctx,
                            "카카오톡 로그인에 실패했습니다.\n${errorResult.errorMessage}")
                    }
                }

                override fun onSessionClosed(errorResult: ErrorResult) {
                }

                override fun onNotSignedUp() {
                }

                override fun onSuccess(userProfile: UserProfile) {
                    Utils.toast(ctx,
                        "카카오톡 로그인에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                }
            })

        }

        override fun onSessionOpenFailed(exception: KakaoException) {
        }
    }

    fun createEmailAccount(email: String, pw: String) { //게스트 계정 생성 (이메일, 비밀번호)
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pw)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Utils.toast(applicationContext,
                        "게스트 회원가입에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                } else {
                    Utils.error(applicationContext,
                        "게스트 회원가입에 실패했습니다.\n${task.exception}")
                }
            }
    }

    fun loginEmailAccount(email: String, pw: String) { //게스트 계정 로그인 (이메일, 비밀번호)
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pw)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Utils.toast(applicationContext,
                        "게스트 로그인에 성공했습니다.",
                        FancyToast.LENGTH_SHORT, FancyToast.SUCCESS)
                } else {
                    Utils.error(applicationContext,
                        "게스트 로그인에 실패했습니다.\n${task.exception}")
                }
            }
    }

}

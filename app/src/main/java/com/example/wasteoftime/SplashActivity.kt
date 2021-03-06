package com.example.wasteoftime

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.os.Handler

//<div>Icons made by <a href="https://www.flaticon.com/authors/freepik" title="Freepik">Freepik</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a></div>

class SplashActivity: AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)

        startAct()
    }

    fun startAct(){
        // This method will be executed once the timer is over
        // Start your app main activity
        Handler().postDelayed({
            if(true) { //checkAppFirstExecute() TODO("최초 실행을 구분할까?")
                startActivity(Intent(this, InfoActivity::class.java))
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            // close this activity
            finish()
        }, 3000)
    }
    fun checkAppFirstExecute(): Boolean{
        val pref = getSharedPreferences("IsFirst" , Activity.MODE_PRIVATE);
        var isFirst = pref.getBoolean("isFirst", false);
        if(!isFirst){ //최초 실행시 true 저장
            val editor = pref.edit();
            editor.putBoolean("isFirst", true);
            editor.commit();
        }
        return !isFirst;
    }
}
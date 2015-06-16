package sj.kist.ssre;

import java.util.Locale;
import java.util.logging.Logger;

import android.app.Service;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.SyncStateContract.Constants;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public class VoiceRecogService extends Service{
	
	protected static final int MSG_VOICE_RECO_RESTART = 1;
	protected static final int MSG_VOICE_RECO_END = 2;
	protected static final int MSG_VOICE_RECO_READY = 3;
	private SpeechRecognizer mSrRecognizer;
	private Boolean mBoolVoiceRecoStarted;
	private ContextWrapper mCtxContext;
	@Override
	public void onCreate() {
	// TODO: 서비스 생성 시 수행할 동작.
		super.onCreate();
		startListening();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
	// TODO: 서비스 바인딩 구현으로 대체한다.
	return null;
	}
	
	private Handler mHdrVoiceRecoState = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case MSG_VOICE_RECO_READY	: break;
				case MSG_VOICE_RECO_END		:
				{
					stopListening();
					sendEmptyMessageDelayed(MSG_VOICE_RECO_RESTART, 1000);
					break;
				}
				case MSG_VOICE_RECO_RESTART	: startListening();	break;
				default:
					super.handleMessage(msg);
			}
		}
	};
	
	public void startListening()
	{
		if(mBoolVoiceRecoStarted == false)
		{
		    if(mSrRecognizer == null)
		    {
			mSrRecognizer = SpeechRecognizer.createSpeechRecognizer(mCtxContext);
			mSrRecognizer.setRecognitionListener(mClsRecoListener);
		    }
		    if(mSrRecognizer.isRecognitionAvailable(mCtxContext))
		    {
			Intent itItent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			itItent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
			itItent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toString());
			itItent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 50);
			mSrRecognizer.startListening(itItent);
		    }
		}
		mBoolVoiceRecoStarted = true;
	}
	
	public void stopListening()
	{
		try
		{
			if (mSrRecognizer != null && mBoolVoiceRecoStarted == true)
			{
				mSrRecognizer.stopListening();
			}
		}
		catch(Exception ex)
		{
			//Logger.e("Stop 예외:"+ StrUtil.trace(ex));
		}
		mBoolVoiceRecoStarted = false;
	}
	
	private RecognitionListener mClsRecoListener = new RecognitionListener()
	{
		@Override
		public void onRmsChanged(float rmsdB)
		{
		}
 
		
		@Override
		public void onResults(Bundle results)
		{
			mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
			
			Intent itBroadcast = new Intent();
			//itBroadcast.setAction(Constants.INTENT_ACTION_VOICE_RECO);        	
			itBroadcast.putExtras(results);
			
			mCtxContext.sendBroadcast(itBroadcast);
		}
		
		@Override
		public void onReadyForSpeech(Bundle params)
		{
		}
 
		@Override
		public void onEndOfSpeech()
		{
		}
 
		@Override
		public void onError(int intError)
		{
			mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
		}
 
		@Override
		public void onBeginningOfSpeech()
		{
		}
 
		@Override
		public void onBufferReceived(byte[] buffer)
		{
		}
 
		@Override
		public void onEvent(int eventType, Bundle params)
		{
		}
 
		@Override
		public void onPartialResults(Bundle partialResults)
		{
		}


	};

}

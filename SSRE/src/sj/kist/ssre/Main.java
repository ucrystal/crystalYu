package sj.kist.ssre;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Main extends Activity {

	private static final String TAG = Main.class.getSimpleName();

	private UsbManager mUsbManager;
	private UsbAccessory mAccessory;
	private ParcelFileDescriptor mFileDescriptor;
	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;

	private static final byte defaultToArduino=0x0;
	private static final byte TARGET_FSRSENSORIN = 0x0;
	private static final byte TARGET_SERVO = 0x1;
	private static final byte TARGET_VRINIT = 0x2;
	private static final byte COMMAND_FSRSENSOR = 0x3;
	
	private static final byte COMMAND_PLEASURE = 0x6;
	private static final byte COMMAND_SORROW = 0x7;
	private static final byte COMMAND_SURPRISE = 0x8;
	private static final byte COMMAND_ANGER = 0x9;
	private static final byte COMMAND_FEAR = 0xa;
	
	private static final byte COMMAND_HUNGRY = 0xb;
	
	private final int THRESHOLD = 300;
	
	private String [] needAttributes = {"hungry", "fatigue", "safety", "interaction", "execute"};
	private int [] initNeed = {0,0,0,0,0}; 
	
	private RelativeLayout face;
	private TextView sensorValueTextView;
	private Intent i;
	private SpeechRecognizer mRecognizer;
	private TextView recognitionResult;
	//private Drawable basic, leftup, leftdown, rightup, rightdown, pleasure, lookdown;
	private TextView batteryState;
	float batteryPct;
	private Need need;
	int touchCount = 0;
	int safetyCount = 0;
	int loveCount = 0;
	int executeCount = 0;
	
	private TimerTask second;
	private TextView execute_text;
	private final Handler handler = new Handler();
	private AudioManager am;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		face = (RelativeLayout) findViewById(R.id.face);
		batteryState = (TextView) findViewById(R.id.batteryState);
		execute_text = (TextView) findViewById(R.id.execute);
		sensorValueTextView = (TextView) findViewById(R.id.sensor_value_textview);
		recognitionResult = (TextView)findViewById(R.id.voice_result);
		
		//usb시리얼 통신을 위한 변수 설정
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		//-- 음성인식 인텐트, 리스너 및 버튼 추가 --//
		i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
	    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
	    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
	    mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
		mRecognizer.setRecognitionListener(recognitionListener);
		findViewById(R.id.voice_btn).setOnClickListener(mClickListener);
	    
	    //진동,소리모드 전환을 위한 오디오매니저
	    am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
	    
		//배터리 충전 상태 값 받아오기
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = this.registerReceiver(null, ifilter);
		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		batteryPct = level / (float)scale;
		
		//욕구 클래스 생성 및 욕구 동기 값 초기화
		need = new Need(this);
		
		touchCount = need.getData("fatigue");
		safetyCount = need.getData("safety");
		loveCount = need.getData("interaction");
		executeCount = need.getData("execute");
		
	    //hungry욕구 상태 체크 및 욕구동기 수행
		need.setData("hungry", (int)(batteryPct*100));
	    checkNeed("hungry");
	    
	    //명령수행욕구 증가 시작
	    execute_text.setText("execute need : "+executeCount);
	    testStart();
		
	    //--화면 터치이벤트 구현--//
		face.setClickable(true);
		face.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				
				float x = event.getX();
				float y = event.getY();
				
				switch (event.getAction()) {
				
				case MotionEvent.ACTION_DOWN:
					touchCount++;
					need.updateData("fatigue", touchCount);
					
					if(x<540 && y<920)
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_leftup));
					else if (x<540 && y>=920)
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_leftdown));
					else if(x>=540 && y<920)
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_rightup));
					else
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_rightdown));
					break;
					
				case MotionEvent.ACTION_MOVE:
					if(x<540 && y<920)
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_leftup));
					else if (x<540 && y>=920)
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_leftdown));
					else if(x>=540 && y<920)
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_rightup));
					else
						face.setBackground(getResources().getDrawable(R.drawable.bg_look_rightdown));
					break;
					
				case MotionEvent.ACTION_UP:
					face.setBackground(getResources().getDrawable(R.drawable.bg_basic));
					checkNeed("safety");
					break;
					
				}
				return false;
			}
		});
	}

	//-- 버튼 클릭 리스너 부분 --//
	Button.OnClickListener mClickListener = new View.OnClickListener() {
		public void onClick(View v) {
			mRecognizer.startListening(i);
		}
	};
	
	//-- 음성인식 리스너 부분 --//
	private RecognitionListener recognitionListener = new RecognitionListener() {
		@Override
        public void onRmsChanged(float rmsdB) {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onResults(Bundle results) {
        	Intent instruction;
        	String key = "";
            key = SpeechRecognizer.RESULTS_RECOGNITION;
            ArrayList<String> mResult = results.getStringArrayList(key);
            String[] rs = new String[mResult.size()];
            mResult.toArray(rs);
            recognitionResult.setText(""+rs[0]);
            
			if (rs[0].matches(".*문자.*")||rs[0].matches(".*보내.*")) {
				if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		if(rs[0].matches(".*한테.*")&&rs[0].matches(".*라고.*")) {
            			String[] who = rs[0].split("한테");
            			Log.v("checkNeed", "문자 이름? : "+who[0]);
                		String s = getPhoneNumber(getApplicationContext(), who[0]);
                		
                		String[] context;
                		String[] context_split;
                		if(rs[0].matches(".*이라고.*")) {
                			context = rs[0].split("이라고");
                			context_split = context[0].split(" ");
                		} else {
                			context = rs[0].split("라고");
                			context_split = context[0].split(" ");
                		}
                		
        				Uri uri = Uri.parse("smsto:"+s);   
        	            instruction = new Intent(Intent.ACTION_SENDTO, uri);   
        	            instruction.putExtra("sms_body", context_split[1]);
        	            startActivity(instruction);
            		} else {
            			Intent intent = new Intent(Intent.ACTION_VIEW);
            			String smsBody = "";
            			intent.putExtra("sms_body", smsBody);
            			intent.putExtra("address", "");
            			intent.setType("vnd.android-dir/mms-sms");
            			startActivity(intent);
            		}
            	}

			}
			else if(rs[0].matches(".*안녕.*")||rs[0].matches(".*귀여워.*")||rs[0].matches(".*귀엽.*")||rs[0].matches(".*고마워.*")||rs[0].matches(".*고맙.*")||rs[0].matches(".*사랑.*")) {
            	if(checkNeed("fatigue")==1) return;	//피곤함이 30이상이면 sleep모드이므로 명령수행없이 음성인식 종료
            	loveCount--;
            	need.updateData("interaction", loveCount);
            	//executeCount++;
            	//need.updateData("execute", executeCount);
            	
            	if(checkNeed("interaction")==0) {
				face.setBackground(getResources().getDrawable(R.drawable.bg_pleasure));
				AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
				sendAccMsg(sndMsg);
            	}
			}
            else if (rs[0].matches(".*쌤.*")||rs[0].matches(".*샘.*")) {
				if(checkNeed("fatigue")==1) return;
					loveCount--;
	            	need.updateData("interaction", loveCount);
					if(checkNeed("interaction")==0) {
						face.setBackground(getResources().getDrawable(R.drawable.bg_surprise));
						AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SURPRISE,TARGET_SERVO, defaultToArduino);		
						sendAccMsg(sndMsg);
					}
			}
			else if (rs[0].matches(".*짜증.*")||rs[0].matches(".*싫어.*")||rs[0].matches(".*미워.*")) {
				if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	//executeCount++;
            	//need.updateData("execute", executeCount);
            	if(checkNeed("interaction")==0) {
					face.setBackground(getResources().getDrawable(R.drawable.bg_angry));
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_ANGER,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
            	}
			}
			else if (rs[0].matches(".*일어나.*")) {
				if(checkNeed("fatigue")==1) {
					touchCount = 0;
					need.updateData("fatigue", (int)(touchCount));
					checkNeed("fatigue");
					face.setBackground(getResources().getDrawable(R.drawable.bg_surprise));
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SURPRISE,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
				}
			}
            else if (rs[0].matches(".*날씨.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
					//face.setBackground(getResources().getDrawable(R.drawable.bg_pleasure));
            		
            		if(rs[0].matches(".*오늘.*")) {
            			instruction = new Intent(Intent.ACTION_WEB_SEARCH);
            			instruction.putExtra(SearchManager.QUERY, "오늘 날씨");
            			startActivity(instruction);
            		} else if(rs[0].matches(".*내일.*")) {
            			instruction = new Intent(Intent.ACTION_WEB_SEARCH);
            			instruction.putExtra(SearchManager.QUERY, "내일 날씨");
            			startActivity(instruction);
            		} else if (rs[0].matches(".*모레.*")) {
            			instruction = new Intent(Intent.ACTION_WEB_SEARCH);
            			instruction.putExtra(SearchManager.QUERY, "모레 날씨");
            			startActivity(instruction);
            		}
            		else {
            			instruction = new Intent(Intent.ACTION_WEB_SEARCH);
            			instruction.putExtra(SearchManager.QUERY, "날씨");
            			startActivity(instruction);
            		}
            	}
			}
            else if (rs[0].matches(".*심심.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount--;
            	need.updateData("interaction", loveCount);
            	//executeCount++;
            	//need.updateData("execute", executeCount);
            	if(checkNeed("interaction")==0) {
            		
            		final CharSequence[] items = {"게임", "동영상", "뉴스"};

            		AlertDialog.Builder builder = new AlertDialog.Builder(Main.this);

            		//알림창의 속성 설정
            		builder.setTitle("재밌는 걸 준비해봤어요!")        // 제목 설정
            		.setItems(items, new DialogInterface.OnClickListener(){    // 목록 클릭시 설정
            			public void onClick(DialogInterface dialog, int index){
            				switch(index) {
            				//Toast.makeText(getApplicationContext(), items[index], Toast.LENGTH_SHORT).show();
            					case 0 ://게임
            	            		try { 
            	            		    Intent intent = new Intent(); 
            	            		    PackageManager pm = getPackageManager(); 
            	            		    intent = pm.getLaunchIntentForPackage("com.king.candycrushsodasaga");
            	            		    startActivity(intent); 
            	            		} catch (Exception e) { 
            	            		    Uri uri = Uri.parse("market://details?id=com.king.candycrushsodasaga"); 
            	            		    Intent i = new Intent(Intent.ACTION_VIEW, uri); 
            	            		    startActivity(i); 
            	            		}
            	            		break;
            					case 1 ://유투브
            						try { 
            	            		    Intent intent = new Intent(); 
            	            		    PackageManager pm = getPackageManager(); 
            	            		    intent = pm.getLaunchIntentForPackage("com.google.android.youtube");
            	            		    startActivity(intent); 
            	            		} catch (Exception e) { 
            	            		    Uri uri = Uri.parse("market://details?id=com.google.android.youtube"); 
            	            		    Intent i = new Intent(Intent.ACTION_VIEW, uri); 
            	            		    startActivity(i); 
            	            		}
            						break;
            					case 2 ://뉴스
            						Intent instruction = new Intent(Intent.ACTION_WEB_SEARCH);
            						instruction.putExtra(SearchManager.QUERY, "뉴스");
            						startActivity(instruction);
            						break;
           					}
            			}
            		});

            		AlertDialog dialog = builder.create();    // 알림창 객체 생성
            		dialog.show();    // 알림창 띄우기

            	}
			}
            else if (rs[0].matches(".*119.*")||rs[0].matches(".*구급차.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
					instruction = new Intent(Intent.ACTION_CALL, Uri.parse("tel:119"));
					startActivity(instruction);
            	}
			}
            else if (rs[0].matches(".*아파.*")||rs[0].matches(".*병원.*")) {
            	if(checkNeed("fatigue")==1) return;
				//face.setBackground();
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SORROW,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
					if(rs[0].matches(".*무릎.*")||rs[0].matches(".*허리.*")) {
						Uri gmmIntentUri = Uri.parse("geo:0,0?q=정형외과");
						Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
						mapIntent.setPackage("com.google.android.apps.maps");
						startActivity(mapIntent);
					}
					else {
						Uri gmmIntentUri = Uri.parse("geo:0,0?q=병원");
						Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
						mapIntent.setPackage("com.google.android.apps.maps");
						startActivity(mapIntent);
					}
            	}
			}
            else if (rs[0].matches(".*찾아.*")||rs[0].matches(".*검색.*")) {
            	if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		String[] words = rs[0].split(" ");
					//AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
					//sendAccMsg(sndMsg);
					
					instruction = new Intent(Intent.ACTION_WEB_SEARCH);
					instruction.putExtra(SearchManager.QUERY, words[0]);
					startActivity(instruction);
            	}
			}
			else if (rs[0].matches(".*시간.*후.*")||rs[0].matches(".*시간.*뒤.*")||rs[0].matches(".*시.*알람.*")) {
				if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
					int position = rs[0].indexOf("시");
					char hour = rs[0].charAt(position-1);
					Log.v("checkNeed", "사용자가 요청한 시간 : "+ hour);
					Calendar calendar = Calendar.getInstance();
					calendar.add(Calendar.HOUR_OF_DAY, (int)hour);		//현재시간에 사용자가 요청한 시간만큼 더함
					Log.v("checkNeed", "현재시간 + 요청한 시간 : "+calendar.get(Calendar.HOUR_OF_DAY));
					int mHour = calendar.get(Calendar.HOUR_OF_DAY);
					int mMin = calendar.get(Calendar.MINUTE);
	
					if(hour != ' ') {
						instruction = new Intent(AlarmClock.ACTION_SET_ALARM);
						instruction.putExtra(AlarmClock.EXTRA_HOUR, mHour);
						instruction.putExtra(AlarmClock.EXTRA_MINUTES, mMin);
						startActivity(instruction);
					}
            	}
			}
			else if (rs[0].matches(".*진동.*")) {
            	if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		String[] words = rs[0].split(" ");
					
            		switch (am.getRingerMode()) {   
            	    case AudioManager.RINGER_MODE_SILENT:   
            	        Log.i("MyApp","Silent mode");
            	        Toast.makeText(getApplicationContext(), "진동모드로 변경되었습니다",Toast.LENGTH_SHORT).show();
            	        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            	        break;
            	    case AudioManager.RINGER_MODE_NORMAL:
            	    	Log.i("MyApp","normal mode");
            	    	Toast.makeText(getApplicationContext(), "진동모드로 변경되었습니다",Toast.LENGTH_SHORT).show();
            	        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            	        break;
            	    }
            	}
			}
			else if (rs[0].matches(".*소리.*")) {
            	if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		String[] words = rs[0].split(" ");
					
            		switch (am.getRingerMode()) {   
            	    case AudioManager.RINGER_MODE_SILENT:   
            	        Log.i("MyApp","Silent mode");
            	        Toast.makeText(getApplicationContext(), "소리모드로 변경되었습니다",Toast.LENGTH_SHORT).show();
            	        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            	        break;
            	    case AudioManager.RINGER_MODE_VIBRATE:
            	    	Log.i("MyApp","normal mode");
            	    	Toast.makeText(getApplicationContext(), "소리모드로 변경되었습니다",Toast.LENGTH_SHORT).show();
            	        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            	        break;
            	    }
            	}
			}
			else if (rs[0].matches(".*카카오.*")) {
            	if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		try { 
            		    Intent intent = new Intent(); 
            		    PackageManager pm = getPackageManager(); 
            		    intent = pm.getLaunchIntentForPackage("com.kakao.talk");
            		    startActivity(intent); 
            		} catch (Exception e) { 
            		    //Log.e("kakao talk execute failed, e=" + e.toString(), null); 
            		    Uri uri = Uri.parse("market://details?id=com.kakao.talk"); 
            		    Intent i = new Intent(Intent.ACTION_VIEW, uri); 
            		    startActivity(i); 
            		}
            	}
			}
			else if (rs[0].matches(".*게임.*")) {
            	if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		try { 
            		    Intent intent = new Intent(); 
            		    PackageManager pm = getPackageManager(); 
            		    intent = pm.getLaunchIntentForPackage("com.king.candycrushsodasaga");
            		    startActivity(intent); 
            		} catch (Exception e) { 
            		    Uri uri = Uri.parse("market://details?id=com.king.candycrushsodasaga"); 
            		    Intent i = new Intent(Intent.ACTION_VIEW, uri); 
            		    startActivity(i); 
            		}
            	}
			}
			else if (rs[0].matches(".*영상.*")||rs[0].matches(".*유투브.*")) {
            	if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		try { 
            		    Intent intent = new Intent(); 
            		    PackageManager pm = getPackageManager(); 
            		    intent = pm.getLaunchIntentForPackage("com.google.android.youtube");
            		    startActivity(intent); 
            		} catch (Exception e) { 
            		    Uri uri = Uri.parse("market://details?id=com.google.android.youtube"); 
            		    Intent i = new Intent(Intent.ACTION_VIEW, uri); 
            		    startActivity(i); 
            		}
            	}
			}
			else if (rs[0].matches(".*전화.*")||rs[0].matches(".*통화.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	execute_text.setText("execute need : "+executeCount);
            	if(checkNeed("interaction")==0) {
            		if(rs[0].matches(".*한테.*")) {
            			String[] words = rs[0].split("한테");
            			Log.v("checkNeed", "전화 이름? : "+words[0]);
                		String s = getPhoneNumber(getApplicationContext(), words[0]);
    					instruction = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"+s));
    					startActivity(instruction);
            		} else {
	            		//전화번호부 띄우기
	            		Intent intent = new Intent(Intent.ACTION_PICK);
	            		intent.setData(ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
	            	    startActivityForResult(intent, 0);
            		}
            	}
			}

        }
         
        @Override
        public void onReadyForSpeech(Bundle params) {
            // TODO Auto-generated method stub
        	
        }
         
        @Override
        public void onPartialResults(Bundle partialResults) {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onEvent(int eventType, Bundle params) {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onError(int error) {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onEndOfSpeech() {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onBufferReceived(byte[] buffer) {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onBeginningOfSpeech() {
            // TODO Auto-generated method stub
             
        }
	};
	
	// 추후에 바뀌지 않는 부분
	Runnable commRunnable = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			int ret = 0;
			byte[] buffer = new byte[3];
			while (ret >= 0) {
				try {
					// 1. 메시지를 액세서리로부터 받는 부분
					ret = mInputStream.read(buffer);
				} catch (IOException e) {
					break;
				}
				// 2. 받은 메시지에 대한 처리 부분
				recvState(buffer);
			}
		}
	};
	
	// 아두이노와 주고 받는 메시지 형식
	// 추후에 바뀌지 않는 부분
	class AccessoryMessage {
		byte command;
		byte target;

		byte value;
		AccessoryMessage(byte command, byte target, byte value) {
			this.command = command;
			this.target = target;
			this.value = value;
		}
		byte getCommand() {return command;}
		byte getTarget() {return target;}
		byte getValue() {return value;}
	}
 
	// 액세서리로부터 받은 메시지를 처리한다
	// 추후에 바뀌지 않는 부분
	void recvState(byte [] buffer) {
		final byte command = buffer[0];
		final byte target = buffer[1];
		final byte value = buffer[2];
		
		// 메시지를 구성한다.
		AccessoryMessage accMsg = new AccessoryMessage(command,target,value);		
				
		// 주 쓰레드에서 수행할 메시지를 만든다
		Message m = Message.obtain(mHandler, command);
		m.obj = accMsg;
		mHandler.sendMessage(m);
	}

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			//-- 5. 액세서리로부터 받은 메시지 처리 시작 부분 --//
			// TODO
			case COMMAND_FSRSENSOR:
				AccessoryMessage accMsg = (AccessoryMessage) msg.obj;
				int soundValue = (accMsg.getValue()&0xFF)<<2;
				
				if(accMsg.getTarget() == TARGET_VRINIT) {
					mRecognizer.startListening(i);
				}
				if(accMsg.getTarget() == TARGET_FSRSENSORIN) {
					sensorValueTextView.setText("sensing value : " + soundValue);
					
	
						if(soundValue >= THRESHOLD) {
							safetyCount++;
							need.updateData("safety", safetyCount);
							
							if(checkNeed("safety")==0) {
								face.setBackground(getResources().getDrawable(R.drawable.bg_sorrow));
								AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SORROW,TARGET_SERVO, defaultToArduino);		
								sendAccMsg(sndMsg);
							} else if(checkNeed("fatigue")==1) {
								touchCount = 15;
								need.updateData("fatigue", (int)(touchCount));
								checkNeed("fatigue");
								face.setBackground(getResources().getDrawable(R.drawable.bg_surprise));
								AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SURPRISE,TARGET_SERVO, defaultToArduino);		
								sendAccMsg(sndMsg);
							}

						} else if(soundValue >= 10) {
							safetyCount--;
							need.updateData("safety", safetyCount);

							loveCount--;
							need.updateData("interaction", loveCount);
							if(checkNeed("interaction")==0) {
								face.setBackground(getResources().getDrawable(R.drawable.bg_pleasure));
								AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
								sendAccMsg(sndMsg);
							}
							
						} else {
							//face.setBackground(getResources().getDrawable(R.drawable.bg_basic));
						}
						
				}
			break;

			//-- 5. 액세서리로부터 받은 메시지 처리 끝 부분 --// 
			default:
				Log.d(TAG, "unknown msg: " 
					+ msg.what);
				break;
			}
		}
	};

	// 새로운 보조 쓰레드를 생성해 액세서리로 메시지를 보내는 부분
	// 추후에 바뀌지 않는 부분
	public void sendAccMsg(AccessoryMessage accMsg) {
		new AsyncTask<AccessoryMessage, Void, Void>() {
			@Override
			protected Void doInBackground(
				AccessoryMessage... params) {
				AccessoryMessage accMsg = 
						params[0];
				
				sendCommand(accMsg.getCommand(),
						accMsg.getTarget(),
						accMsg.getValue());
				
				return null;
			}
		}.execute((AccessoryMessage)accMsg);		
	}
	
	// 액세서리로 명령 보낼 때 호출
	// 추후에 바뀌지 않는 부분
	public void sendCommand(byte command, byte target, int value) {
		byte[] buffer = new byte[3];
		if (value > 255)
			value = 255;

		buffer[0] = command;
		buffer[1] = target;
		buffer[2] = (byte) value;
		if (mOutputStream != null && buffer[1] != -1) {
			try {
				mOutputStream.write(buffer);
			} catch (IOException e) {
				Log.e(TAG, "write failed", e);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; 
		// this adds items to the action bar 
		// if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = 
		 	(accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {  
				// USB 액세서리 연결창에서 확인을 누르면
				openAccessory(accessory);
			} 
		}
		int batteryStateShow = (int)(batteryPct*100);
		batteryState.setText(Integer.toString(batteryStateShow));
		WindowManager.LayoutParams myLayoutParameter = getWindow().getAttributes();
		myLayoutParameter.screenBrightness = batteryPct;
		getWindow().setAttributes(myLayoutParameter);

	}
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	    //-- Need 클래스 생성 --//
	    
	    //need.setData(needAttributes, initNeed);	
		need = new Need(this);
	    //hungry욕구 설정
		need.updateData("hungry", (int)(batteryPct*100));
		need.updateData("fatigue", touchCount);
		need.updateData("safety", safetyCount);
		need.updateData("interaction", loveCount);
		need.updateData("execute", executeCount);
	    
		closeAccessory();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unregisterReceiver(mUsbReceiver);
	}
	
	private final BroadcastReceiver mUsbReceiver = 
			new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			
			String action = intent.getAction();
			if (UsbManager
				.ACTION_USB_ACCESSORY_DETACHED
				.equals(action)) { 
				// 리셋 버튼을 두 번 누르거나, 
				// USB 연결을 끊었을 때
				UsbAccessory accessory = 
				intent.getParcelableExtra(
				UsbManager.EXTRA_ACCESSORY); 
				if (accessory != null && 
					accessory.equals(mAccessory)) {
					closeAccessory();
					// USB 연결 해지시 앱 종료
					finish();
				}
			}
		}		
	};
	
	//주소록의 이름으로 번호 구하는 함수
	private String getPhoneNumber(Context context, String strName)
	{
	    Cursor phoneCursor = null;
	    String strReturn = "";
	    try
	    {
	        // 주소록이 저장된 URI
	        Uri uContactsUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
	 
	        // 주소록의 이름과 전화번호의 이름
	        String strProjection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
	 
	        // 주소록을 얻기 위한 쿼리문을 날리고 커서를 리턴 (이름으로 정렬해서 가져옴)
	        phoneCursor = context.getContentResolver().query(uContactsUri,
	            null, null, null, strProjection);
	        phoneCursor.moveToFirst();
	        //Log.d("TAG", "AddressMgr address count = " + phoneCursor.getCount());
	 
	        String name = "";
	        String number = "";
	        String email = "";
	        int nameColumn = phoneCursor.getColumnIndex(Phone.DISPLAY_NAME);
	        int numberColumn = phoneCursor.getColumnIndex(Phone.NUMBER);
	        int NumberTypeColumn = phoneCursor.getColumnIndex(Phone.TYPE);
	 
	        // stop loop if find
	        while (!phoneCursor.isAfterLast() && strReturn.equals(""))
	        {
	            name = phoneCursor.getString(nameColumn);
	            number = phoneCursor.getString(numberColumn);
	            int numberType = Integer.valueOf(phoneCursor.getString(NumberTypeColumn));
	 
	            // Log.d("AddressMgr", "AddressMgr  name : " + name +
	            // "    nunmber : " + number + "   email : " + email);
	 
	            // if find, set return values, and stop loops.
	            if(name.matches(".*"+strName+".*"))
	            {
	                strReturn = number;
	            }
	 
	            name = "";
	            number = "";
	            email = "";
	            phoneCursor.moveToNext();
	        }
	    }
	    catch (Exception e)
	    {
	        //Log.e("[GetPhonenumberAdapter] getContactData", e.toString());
	    }
	    finally
	    {
	        if (phoneCursor != null)
	        {
	            phoneCursor.close();
	            phoneCursor = null;
	        }
	    }
	 
	    return strReturn;
	}
	
	//주소록 리스트 선택 시 자동실행
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_OK)
		{
				Cursor cursor = getContentResolver().query(data.getData(), 
						new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, 
					ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
				cursor.moveToFirst();
	 	                //mName.setText(cursor.getString(0));       //이름
	            		//mNumber.setText(cursor.getString(1));     //번호
	            		Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"+cursor.getString(1)));
	            		startActivity(intent);
	            cursor.close();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	public void testStart() {

		second = new TimerTask() {

			@Override
			public void run() {
				int currentExecuteNeed = need.getData("execute");
				
				//사용자가 오랫동안 아무 작업도 안할 시 업데이트 중지
				if(currentExecuteNeed>=15)
					return;
				
				Update();
				
			}
		};
		Timer timer = new Timer();
		timer.schedule(second, 0, 90000);
	}

	protected void Update() {
		Runnable updater = new Runnable() {
			public void run() {
				execute_text.setText("execute need : "+executeCount);
				checkNeed("execute");
				executeCount++;
				need.updateData("execute", executeCount);
			}
		};
		handler.post(updater);
	}


	private void openAccessory(UsbAccessory accessory) {
		// TODO Auto-generated method stub
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Thread thread = new Thread(null, commRunnable, TAG);
			thread.start();
		} else {
		}
	}
	
	private void closeAccessory() {
		// TODO Auto-generated method stub
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}
	
	private int checkNeed(String needVar) {
		if(needVar.equals("hungry")) {
			int currentHungry = need.getData(needVar);
			if (currentHungry < 20) {
		    	Log.v("checkNeed", "hungry 60미만 조건");
		    	face.setBackground(getResources().getDrawable(R.drawable.bg_hungry));
				AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_HUNGRY,TARGET_SERVO, defaultToArduino);		
				sendAccMsg(sndMsg);
				return 1;
		    } else
		    	return 0;
		} else if (needVar.equals("fatigue")) {
			if(checkNeed("hungry")==0) {				//하위 욕구 검사 결과, 동기 수행하지 않을 경우에만 상위 욕구 검사!
				int currentFatigue = need.getData(needVar);
				if(currentFatigue >= 30) {
					Log.v("checkNeed", "fatigue 30이상 조건");
					face.setBackground(getResources().getDrawable(R.drawable.bg_sleep));
					face.setEnabled(false);
					return 1;
				} else if(currentFatigue >= 15) {
					Log.v("checkNeed", "fatigue 15이상 조건");
			    	face.setBackground(getResources().getDrawable(R.drawable.bg_tired));
			    	face.setEnabled(true);
					//AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SORROW,TARGET_SERVO, defaultToArduino);
					//sendAccMsg(sndMsg);
			    	return 2;
				} else {
					Log.v("checkNeed", "little fatigue");
					face.setEnabled(true);
					//face.setBackground(getResources().getDrawable(R.drawable.bg_basic));
					return 0;
				}
			} else
				return 3;
			
		} else if (needVar.equals("safety")) {
			if(checkNeed("hungry")==0 && (checkNeed("fatigue")==0||checkNeed("fatigue")==2)) {	//하위 욕구 결과가 모두 동기 발현되지 않아야 상위 욕구 검사 수행!
				int currentSafetyneed = need.getData(needVar);
				if(currentSafetyneed >= 5) {
			    	Log.v("checkNeed", "safety need 5이상 조건");
			    	face.setBackground(getResources().getDrawable(R.drawable.bg_fear));
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_FEAR,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
					return 1;
				} else
					return 0;
			} else	//하위욕구 중에 하나가 동기 조건에 걸릴 경우
				return 3;
		} else if (needVar.equals("interaction")) {
			if(checkNeed("hungry")==0 && (checkNeed("fatigue")==0||checkNeed("fatigue")==2) && checkNeed("safety")==0){
				int currentLoveneed = need.getData(needVar);
				if(currentLoveneed >= 5) {
			    	Log.v("checkNeed", "love need 5이상 조건");
			    	face.setBackground(getResources().getDrawable(R.drawable.bg_needlove));
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
					return 1;
				} else
					return 0;
			} else
				return 3;
		} else if (needVar.equals("execute")) {
			if(checkNeed("hungry")==0 && (checkNeed("fatigue")==0||checkNeed("fatigue")==2) && checkNeed("safety")==0 && checkNeed("interaction")==0){
				int currentExecuteneed = need.getData(needVar);
				if(currentExecuteneed >= 10) {
					Log.v("checkNeed", "execute need 5이상 조건");
			    	face.setBackground(getResources().getDrawable(R.drawable.bg_needinst));
			    	AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
					return 1;
				} else
					return 0;
			} else
				return 3;
			
		}
		
		return 0;
	}
}

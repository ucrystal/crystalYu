package sj.kist.ssre;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.AlarmClock;
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		//--화면이미지 설정--//
		//basic = getResources().getDrawable(R.drawable.bg_basic);
		
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		//-- 음성인식 인텐트, 리스너 및 버튼 추가 --//
		i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
	    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
	    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
	    
	    mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
		mRecognizer.setRecognitionListener(recognitionListener);
		recognitionResult = (TextView)findViewById(R.id.voice_result);
	    findViewById(R.id.voice_btn).setOnClickListener(mClickListener);
	    sensorValueTextView = (TextView) findViewById(R.id.sensor_value_textview);
		batteryState = (TextView) findViewById(R.id.batteryState);
		//--화면변경을 위한 변수 설정--//
	    face = (RelativeLayout) findViewById(R.id.face);
		
		//배터리 충전 상태 값 받아오기
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = this.registerReceiver(null, ifilter);
		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		batteryPct = level / (float)scale;
		
	    //-- Need 클래스 생성 --//
	    need = new Need(this);
	    need.setData(needAttributes, initNeed);	

	    //hungry욕구 설정
		need.updateData("hungry", (int)(batteryPct*100));
	    
	    //hungry욕구 상태 체크 및 욕구동기 수행
	    checkNeed("hungry");

	    //배터리 상태값 출력
		//int hungry = need.getData("hungry");
		//batteryState.setText(Integer.toString(hungry));
		
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
            
            if(rs[0].matches(".*안녕.*")) {
            	if(checkNeed("fatigue")==1) return;	//피곤함이 30이상이면 sleep모드이므로 명령수행없이 음성인식 종료
            	loveCount--;
            	need.updateData("interaction", loveCount);
            	executeCount++;
            	need.updateData("execute", executeCount);
            	
            	if(checkNeed("execute")==0) {
				face.setBackground(getResources().getDrawable(R.drawable.bg_pleasure));
				AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
				sendAccMsg(sndMsg);
            	}
			}
            else if (rs[0].matches(".*날씨.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	if(checkNeed("execute")==0) {
					face.setBackground(getResources().getDrawable(R.drawable.bg_pleasure));
					instruction = new Intent(Intent.ACTION_WEB_SEARCH);
					instruction.putExtra(SearchManager.QUERY, "날씨");
					startActivity(instruction);
            	}
			}
            else if (rs[0].matches(".*심심.*")||rs[0].matches(".*뉴스.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	if(checkNeed("execute")==0) {
					face.setBackground(getResources().getDrawable(R.drawable.bg_pleasure));
					instruction = new Intent(Intent.ACTION_WEB_SEARCH);
					instruction.putExtra(SearchManager.QUERY, "뉴스");
					startActivity(instruction);
            	}
			}
            else if (rs[0].matches(".*119.*")||rs[0].matches(".*구급차.*")) {
            	if(checkNeed("fatigue")==1) return;
            	loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount--;
            	need.updateData("execute", executeCount);
            	if(checkNeed("execute")==0) {
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_SURPRISE,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
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
            	if(checkNeed("execute")==0) {
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
            	if(checkNeed("execute")==0) {
            		String[] words = rs[0].split(" ");
					AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PLEASURE,TARGET_SERVO, defaultToArduino);		
					sendAccMsg(sndMsg);
					
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
            	if(checkNeed("execute")==0) {
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
			else if (rs[0].matches(".*짜증.*")||rs[0].matches(".*싫어.*")) {
				if(checkNeed("fatigue")==1) return;
				loveCount++;
            	need.updateData("interaction", loveCount);
            	executeCount++;
            	need.updateData("execute", executeCount);
            	if(checkNeed("execute")==0) {
	            	need.updateData("execute", executeCount);
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
							}

						} else if(soundValue >= 30) {
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
				if(currentExecuteneed >= 5) {
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

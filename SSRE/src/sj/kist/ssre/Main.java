package sj.kist.ssre;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
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

	//-- 2. 필요 속성 추가 시작 부분 --//
	// TODO
	private static final byte COMMAND_PIEZOBUZZERSENSOR = 0x3;
	private static final byte TARGET_PIEZOBUZZERPIN = 0x0;
	
	private static final byte COMMAND_PETTING = 0x4;
	private static final byte COMMAND_HITTING = 0x5;
	private static final byte TARGET_SERVO = 0x1;
	
	private RelativeLayout face;
	private TextView soundValueTextView;
	
	//private ProgressBar soundValueProgressBar;
	//private Random random;
	private final int THRESHOLD = 100;
	private Intent i;
	
	//-- 2. 필요 속성 추가 끝 부분 --//
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

		IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		setContentView(R.layout.main);
		
		//-- 음성인식 인텐트 추가 --//
		i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
	    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
	    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
		
		//-- 3. 추가 속성 초기화 시작 부분 --//
		// TODO
		face = (RelativeLayout) findViewById(R.id.face);
		soundValueTextView = (TextView) findViewById(R.id.sensor_value_textview);
		Drawable basic = getResources().getDrawable(R.drawable.bg_basic);
		
		//random = new Random(System.currentTimeMillis());
		//-- 3. 추가 속성 초기화 끝 부분 --//
		
		face.setBackground(basic);
		face.setClickable(true);
		face.setOnTouchListener(new View.OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				Drawable basic = getResources().getDrawable(R.drawable.bg_basic);
				Drawable leftup = getResources().getDrawable(R.drawable.bg_look_leftup);
				Drawable leftdown = getResources().getDrawable(R.drawable.bg_look_leftdown);
				Drawable rightup = getResources().getDrawable(R.drawable.bg_look_rightup);
				Drawable rightdown = getResources().getDrawable(R.drawable.bg_look_rightdown);
				
				float x = event.getX();
				float y = event.getY();
				
				switch (event.getAction()) {
				
				case MotionEvent.ACTION_DOWN:
					if(x<540 && y<920)
						face.setBackground(leftup);
					else if (x<540 && y>=920)
						face.setBackground(leftdown);
					else if(x>=540 && y<920)
						face.setBackground(rightup);
					else
						face.setBackground(rightdown);

					Toast.makeText(getApplicationContext(), "x="+x+" y="+y, Toast.LENGTH_SHORT).show();
					break;
					
				case MotionEvent.ACTION_UP:
					face.setBackground(basic);
					break;
				}
				return false;
			}
		});
	}

	//-- 4. 위젯 리스너 함수 시작 부분 --//
	// TODO
	//-- 4. 위젯 리스너 함수 끝 부분 --//
	
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
		AccessoryMessage accMsg = 
			new AccessoryMessage(command,target,value);		
				
		// 주 쓰레드에서 수행할 메시지를 만든다
		Message m = Message.obtain(
			mHandler, command);
		m.obj = accMsg;
		mHandler.sendMessage(m);
	}

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Drawable basic = getResources().getDrawable(R.drawable.bg_basic);
			Drawable lookdown = getResources().getDrawable(R.drawable.bg_lookdown);
			Drawable pleasure = getResources().getDrawable(R.drawable.bg_pleasure);
			switch (msg.what) {
			//-- 5. 액세서리로부터 받은 메시지 처리 시작 부분 --//
			// TODO
			case COMMAND_PIEZOBUZZERSENSOR:
				AccessoryMessage accMsg = (AccessoryMessage) msg.obj;
				int soundValue = (accMsg.getValue()&0xFF)<<2;
				if(accMsg.getTarget() == TARGET_PIEZOBUZZERPIN) {
					//soundValueProgressBar.setProgress(soundValue);
					soundValueTextView.setText("Sound Value : " + soundValue);
					
					if(soundValue >= THRESHOLD) {
						//face.setBackgroundColor(Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256)));
						face.setBackground(lookdown);
						AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_HITTING,TARGET_SERVO, accMsg.getValue());		
						sendAccMsg(sndMsg);
					} else if(soundValue >= 10) {
						face.setBackground(pleasure);
						AccessoryMessage sndMsg = new AccessoryMessage(COMMAND_PETTING,TARGET_SERVO, accMsg.getValue());		
						sendAccMsg(sndMsg);
					} else {
						face.setBackground(basic);
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
			FileDescriptor fd = 
					mFileDescriptor.getFileDescriptor();
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
}

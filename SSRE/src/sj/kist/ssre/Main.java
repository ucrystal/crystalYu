package sj.kist.ssre;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
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

	private static final byte COMMAND_PIEZOBUZZERSENSOR = 0x3;
	private static final byte TARGET_PIEZOBUZZERPIN = 0x0;
	
	private static final byte COMMAND_PETTING = 0x4;
	private static final byte COMMAND_HITTING = 0x5;
	private static final byte TARGET_SERVO = 0x1;
	private final int THRESHOLD = 100;
	
	private RelativeLayout face;
	private TextView soundValueTextView;
	private Intent i;
	private SpeechRecognizer mRecognizer;
	private TextView recognitionResult;
	private Drawable basic, leftup, leftdown, rightup, rightdown, pleasure, lookdown;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		basic = getResources().getDrawable(R.drawable.bg_basic);

		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		
		setContentView(R.layout.main);
		
		soundValueTextView = (TextView) findViewById(R.id.sensor_value_textview);
		
		//-- �����ν� ����Ʈ, ������ �� ��ư �߰� --//
		i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
	    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
	    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
	    
	    mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
		mRecognizer.setRecognitionListener(recognitionListener);
		recognitionResult = (TextView)findViewById(R.id.voice_result);
	    findViewById(R.id.voice_btn).setOnClickListener(mClickListener);
	    
		//--ȭ�麯���� ���� ���� ����--//
		face = (RelativeLayout) findViewById(R.id.face);
		
		//--ȭ���̹��� ���� �� ��ġ�̺�Ʈ ����--//
		face.setBackground(basic);
		face.setClickable(true);
		face.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				basic = getResources().getDrawable(R.drawable.bg_basic);
				leftup = getResources().getDrawable(R.drawable.bg_look_leftup);
				leftdown = getResources().getDrawable(R.drawable.bg_look_leftdown);
				rightup = getResources().getDrawable(R.drawable.bg_look_rightup);
				rightdown = getResources().getDrawable(R.drawable.bg_look_rightdown);
				
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

	//-- ��ư Ŭ�� ������ �κ� --//
	Button.OnClickListener mClickListener = new View.OnClickListener() {
		public void onClick(View v) {
			mRecognizer.startListening(i);
		}
	};
	
	//-- �����ν� ������ �κ� --//
	private RecognitionListener recognitionListener = new RecognitionListener() {
		@Override
        public void onRmsChanged(float rmsdB) {
            // TODO Auto-generated method stub
             
        }
         
        @Override
        public void onResults(Bundle results) {
    		pleasure = getResources().getDrawable(R.drawable.bg_pleasure);
    		
        	String key = "";
            key = SpeechRecognizer.RESULTS_RECOGNITION;
            ArrayList<String> mResult = results.getStringArrayList(key);
            String[] rs = new String[mResult.size()];
            mResult.toArray(rs);
            if(rs[0].matches(".*�ȳ�.*")) {
				face.setBackground(pleasure);
				try {
				    Thread.sleep(2000);
				} catch(InterruptedException ex) {
				    Thread.currentThread().interrupt();
				}
			}
			recognitionResult.setText(""+rs[0]);
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
	
	// ���Ŀ� �ٲ��� �ʴ� �κ�
	Runnable commRunnable = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			int ret = 0;
			byte[] buffer = new byte[3];
			while (ret >= 0) {
				try {
					// 1. �޽����� �׼������κ��� �޴� �κ�
					ret = mInputStream.read(buffer);
				} catch (IOException e) {
					break;
				}
				// 2. ���� �޽����� ���� ó�� �κ�
				recvState(buffer);
			}
		}
	};
	
	// �Ƶ��̳�� �ְ� �޴� �޽��� ����
	// ���Ŀ� �ٲ��� �ʴ� �κ�
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
 
	// �׼������κ��� ���� �޽����� ó���Ѵ�
	// ���Ŀ� �ٲ��� �ʴ� �κ�
	void recvState(byte [] buffer) {
		final byte command = buffer[0];
		final byte target = buffer[1];
		final byte value = buffer[2];
		
		// �޽����� �����Ѵ�.
		AccessoryMessage accMsg = 
			new AccessoryMessage(command,target,value);		
				
		// �� �����忡�� ������ �޽����� �����
		Message m = Message.obtain(
			mHandler, command);
		m.obj = accMsg;
		mHandler.sendMessage(m);
	}

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			basic = getResources().getDrawable(R.drawable.bg_basic);
			lookdown = getResources().getDrawable(R.drawable.bg_lookdown);
			pleasure = getResources().getDrawable(R.drawable.bg_pleasure);
			switch (msg.what) {
			//-- 5. �׼������κ��� ���� �޽��� ó�� ���� �κ� --//
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

			//-- 5. �׼������κ��� ���� �޽��� ó�� �� �κ� --// 
			default:
				Log.d(TAG, "unknown msg: " 
					+ msg.what);
				break;
			}
		}
	};

	// ���ο� ���� �����带 ������ �׼������� �޽����� ������ �κ�
	// ���Ŀ� �ٲ��� �ʴ� �κ�
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
	
	// �׼������� ��� ���� �� ȣ��
	// ���Ŀ� �ٲ��� �ʴ� �κ�
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
				// USB �׼����� ����â���� Ȯ���� ������
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
				// ���� ��ư�� �� �� �����ų�, 
				// USB ������ ������ ��
				UsbAccessory accessory = 
				intent.getParcelableExtra(
				UsbManager.EXTRA_ACCESSORY); 
				if (accessory != null && 
					accessory.equals(mAccessory)) {
					closeAccessory();
					// USB ���� ������ �� ����
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

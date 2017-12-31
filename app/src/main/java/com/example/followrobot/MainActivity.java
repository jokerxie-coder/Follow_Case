package com.example.followrobot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
	
	private GestureDetector gestureDetector;
	
	private final static int REQUEST_CONNECT_DEVICE = 1;    //宏定义查询设备句柄
	
	private final static String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";   //SPP服务UUID号
	
	private InputStream is;    //输入流，用来接收蓝牙数据
    private String smsg = "";    //显示用数据缓存
    private BluetoothDevice device = null;     //蓝牙设备
    private BluetoothSocket socket = null;      //蓝牙通信socket

	private boolean bRun=false;//接收数据线程运行标志位
    private ActionBar actionBar;
    private SharedPreferences sp;
    private Editor editor;
    private String ifSwitch ;
    private Button bu_turnon;
    
    private BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();    //获取本地蓝牙适配器，即蓝牙设备
	private SensorManager sensorManager;
	
	private float rotate;// 防止多次移动，角度出现问题，在每次移动时，先移动回来。

	private MySensorEventListener listener;


	//方位角
	private TextView tv_nowAngle;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);   //设置画面为主画面 main.xml
        
        
        actionBar = getActionBar();
        sp = getSharedPreferences("config", 0);
        editor = sp.edit();
        
        bu_turnon = (Button)findViewById(R.id.bu_turnon);
        tv_nowAngle=(TextView)findViewById(R.id.tv_nowAngle);
        
        // 1、获取一个SensorManager
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        // 2、获取一个指定type的传感器
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);// 方向传感器，使用deprecated的，更好的兼容低版本。
        // 3、注册一个监听器
        listener = new MySensorEventListener();
        sensorManager.registerListener(listener , sensor, SensorManager.SENSOR_DELAY_FASTEST);

    
        ifSwitch = sp.getString("switch", null);
        if(TextUtils.isEmpty(ifSwitch)){
        	editor.putString("switch", "on");
        	editor.commit();
        }


        /**
		 * 检测用户的手势，并做出相应的处理
		 */
        gestureDetector = new GestureDetector(getApplicationContext(), new OnGestureListener() {
			
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				return false;
			}
			
			@Override
			public void onShowPress(MotionEvent e) {
			}
			
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
					float distanceY) {
				return false;
			}
			
			@Override
			public void onLongPress(MotionEvent e) {
			}
			
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
					float velocityY) {
				if(Math.abs(velocityY)<200){
					return false;
				}
				//向下滑动
				if(Math.abs(e2.getRawX() - e1.getRawX())<400&&(e2.getRawY() - e1.getRawY())>40){
					actionBar.show();
					
				}
				//向上滑动
				if(Math.abs(e2.getRawX() - e1.getRawX())<400&&(e1.getRawY() - e2.getRawY())>40){
					actionBar.hide();
					
				}
				return true;
			}
			
			@Override
			public boolean onDown(MotionEvent e) {

				return false;
			}
		});
        
       //如果打开本地蓝牙设备不成功，提示信息，结束程序
        if (bluetooth == null){
        	Toast.makeText(this, "无法打开手机蓝牙，请确认手机是否有蓝牙功能！", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        //开启线程设置设备可以被搜索
       new Thread(){
    	   public void run(){

    		   if(bluetooth.isEnabled()==false){
        		bluetooth.enable();
    		   }
    	   }   	   
       }.start();      
    }


    /**
	 * 处理屏幕点击事件
	 * @param event
	 * @return
     */
    public boolean onTouchEvent(MotionEvent event) {
    	gestureDetector.onTouchEvent(event);
    	return super.onTouchEvent(event);
    }


	//创建线程对象
    Thread m=new Thread(new MyThread());

	/**
	 * 开始跟踪按钮点击事件
	 * @param view
     */
    public void onTurnOnClicked(View view){
    	if(sp.getBoolean("isConnected", false)==true){
    		if(sp.getString("switch", null).equals("on")){
        		m.start();//开启线程
        		editor.putString("switch", "off");
        		editor.commit();
        		bu_turnon.setBackgroundResource(R.drawable.switch_off);
        		bu_turnon.setText("结束跟踪");
        	}else{
        		m.interrupt();//中断线程
        		editor.putString("switch", "on");
        		editor.commit();
        		bu_turnon.setText("开始跟踪");
        		bu_turnon.setBackgroundResource(R.drawable.switch_on);
        	}
    	}else{

    		Toast.makeText(getApplicationContext(), "请与智能跟踪机器人连接", Toast.LENGTH_SHORT).show();
    	}
    	
    }


    /**
	 * 初始化右上角的菜单选项的布局
	 * @param menu
	 * @return
	 */
    public boolean onCreateOptionsMenu(Menu menu) {
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
	 * 处理右上角的菜单选项的点击事件
	 * @param item
	 * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	switch (item.getItemId()) {
    	
		case R.id.quit://退出
			editor.putBoolean("isConnected", false);
	    	editor.commit();
			finish();
			break;
		case android.R.id.home://home键
			editor.putBoolean("isConnected", false);
	    	editor.commit();
			finish();
			break;
		case R.id.about://说明
			Intent intent = new Intent(MainActivity.this,AboutActivity.class);
			startActivity(intent);
			break;

		default:
			break;
		}
    	
    	return super.onOptionsItemSelected(item);
    }


    /**
	 * 向单片机蓝牙模块发送命令
	 * @param command
     */
    public void sendCommand(String command){
    	int i ;
    	int n = 0;
    	if(socket!=null) {
			try {
				OutputStream os = socket.getOutputStream();
				byte[] bos = command.getBytes();
				for(i=0;i<bos.length;i++){
					if(bos[i]==0x0a)
						n++;
				}
				byte[] bos_new = new byte[bos.length+n];
				n=0;
				for(i=0;i<bos.length;i++){ //手机中换行为0a,将其改为0d0a后再发送
					if(bos[i]==0x0a){
						bos_new[n]=0x0d;
						n++;
						bos_new[n]=0x0a;
					}else{
						bos_new[n]=bos[i];
					}
					n++;
				}
				os.write(bos_new);
				Toast.makeText(getApplicationContext(), "发送数据成功！", Toast.LENGTH_SHORT).show();
			} catch (IOException e) {

				Toast.makeText(getApplicationContext(), "发送数据失败！", Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}
    	}
    	else
    	{
    		Toast.makeText(getApplicationContext(), "请与智能跟踪机器人连接", Toast.LENGTH_SHORT).show();
    	}
    }
    
    //接收活动结果，响应startActivityForResult()
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode){
    	case REQUEST_CONNECT_DEVICE:     //连接结果，由DeviceListActivity设置返回
    		// 响应返回结果
            if (resultCode == Activity.RESULT_OK) {   //连接成功，由DeviceListActivity设置返回
                // MAC地址，由DeviceListActivity设置返回
                String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // 得到蓝牙设备句柄      
                device = bluetooth.getRemoteDevice(address);
 
                // 用服务号得到socket
                try{
                	socket = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
                }catch(IOException e){
                	Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                }
                //连接socket
            	Button btn = (Button) findViewById(R.id.Button03);
                try{
                	socket.connect();
                	Toast.makeText(this, "连接"+device.getName()+"成功！", Toast.LENGTH_SHORT).show();
                	editor.putBoolean("isConnected", true);
                	editor.commit();
                	btn.setText("断开");
                }catch(IOException e){
                	try{
                		Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                		socket.close();
                		socket = null;
                	}catch(IOException ee){
                		Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                	}
                	return;
                }
                
                //打开接收线程

                try{
            		is = socket.getInputStream();   //得到蓝牙数据输入流
            		}catch(IOException e){
            			Toast.makeText(this, "接收数据失败！", Toast.LENGTH_SHORT).show();
            			return;
            		}

					//如果接收数据线程未开启，则开启接收数据线程，
				    //并将接收数据线程标志位置为true
					if(bRun==false){
						ReadThread.start();
						bRun=true;
					}

            }
    		break;
    	default:break;
    	}
    }
    
    //接收数据线程
    Thread ReadThread=new Thread(){


        /**
		 * 线程的run()方法只会执行一次
		 * 因此要想线程一直运行，就得在run()方法中加一个while()循环
		 */
    	public void run(){
    		int num;
    		byte[] buffer = new byte[1024];
    		byte[] buffer_new = new byte[1024];
    		int i ;
    		int n ;
    		//接收线程
    		while(bRun){
    			try{
					num = is.read(buffer);         //读入数据
					n=0;

					//将服务器端发送过来的换行0x0d0a转换为手机识别的换行0a
					//其中0x0d0a和0a均为字符的十六进制数表示
					for(i=0;i<num;i++){
						if((buffer[i] == 0x0d)&&(buffer[i+1]==0x0a)){
							buffer_new[n] = 0x0a;
							i++;
						}else{
							buffer_new[n] = buffer[i];
						}
						n++;
					}
					String s = new String(buffer_new,0,n);
					smsg+=s;   //写入接收缓存

					//如果短时间没有数据，
					//则利用Handler发送消息通知UI线程显示接收的数据
					//同时跳出while循环
					if(is.available()==0){

						//需要数据传递，用下面方法；
						Message msg = new Message();
						msg.obj = smsg;//可以是基本类型，可以是对象，可以是List、map等；
						receiveHandler.sendMessage(msg);
						break;
					}

				}catch(IOException e){
					e.printStackTrace();
				}
    		}
    	}
    };

    /**
	 * 处理接收单片机蓝牙模块发送过来的数据
	 */
    Handler receiveHandler= new Handler(){
    	public void handleMessage(Message msg){
    		super.handleMessage(msg);

			Toast.makeText(MainActivity.this,"接收到的数据为"+msg.obj.toString(), Toast.LENGTH_SHORT).show();
    	}
    };
    
    //关闭程序掉用处理部分
    public void onDestroy(){
    	super.onDestroy();
    	if(socket!=null)  //关闭连接socket
    	try{
			bRun=false;//停止接收线程
    		socket.close();

    	}catch(IOException e){

			e.printStackTrace();
		}
    		bluetooth.disable();  //关闭蓝牙服务

		//Java的Thread类提供的destroy和stop方法无法正确终止线程，
		// 只能通过标志或者interrupt()方法来进行。
		m.interrupt();

    	sensorManager.unregisterListener(listener);
        listener = null;
    }
    
    
    
    //连接按键响应函数
    public void onConnectButtonClicked(View v){ 
    	if(bluetooth.isEnabled()==false){  //如果蓝牙服务不可用则提示
    		Toast.makeText(this, " 打开蓝牙中...", Toast.LENGTH_LONG).show();
    		return;
    	}
    	
    	
        //如未连接设备则打开DeviceListActivity进行设备搜索
    	Button btn = (Button) findViewById(R.id.Button03);
    	if(socket==null){
    		Intent serverIntent = new Intent(this, DeviceListActivity.class); //跳转程序设置
    		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);  //设置返回宏定义
    	}
    	else{
    		bu_turnon.setBackgroundResource(R.drawable.switch_off);
    		 //关闭连接socket
    	    try{
    	    	
    	    	is.close();
    	    	socket.close();
    	    	socket = null;
    	    	bRun = false;//停止接收数据线程
    	    	btn.setText("连接");
    	    	
    	    }catch(IOException e){
				e.printStackTrace();
			}
    	    editor.putBoolean("isConnected", false);
	    	editor.commit();
    	}
    	return;
    }


    /**
	 * 使用Handler来完成定时操作
     * 使用线程每隔5000ms向单片机蓝牙模块发送一次Android控制端当前的方向角
	 */
	public class MyThread implements Runnable{
    	@Override
    	public void run(){

    		while(true)
    		{
    			try{
    				
    				Message message=new Message();
    				message.what=1;
					sendHandler.sendMessage(message);
    				Thread.sleep(5000); //线程睡眠5000ms
    				}
    			catch(InterruptedException e)
    			{
    				e.printStackTrace();
    			}
    		}
    	}
    }

    /**
	 *处理定时发送方向角的Handler
	 */
    Handler sendHandler=new Handler(){
    	public void handleMessage(Message msg){

			//发送方向角给单片机控制端
    		sendCommand(String.valueOf(getrotate()));
    	}
    };
    
    /**
     * 定义成内部类，而不是匿名内部类，因为取消注册的时候需要用到
     */
    private class MySensorEventListener implements SensorEventListener {
 
        @Override
        public void onSensorChanged(SensorEvent event) {
            // 正北方向，第一次移动30°，第二次移动30°，
            float[] values = event.values;
            // 0=North, 90=East, 180=South, 270=West
            // float light = values[0];// 对于光线传感器来说，values[0]:代表光线的强弱
            //float jiaodu = values[0];// 对于方向传感器来说，values[0]:代表的是与正北方向的角度，正北为0，查看api
            //System.out.println("与正北的夹角：" + jiaodu);
            rotate = values[0];
            tv_nowAngle.setText(String.valueOf((int)rotate));
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }
    public int getrotate()
    {
    	return (int)rotate;
    }
	

}

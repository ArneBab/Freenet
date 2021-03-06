/**
 * Connection thread which has the acquired sockets
 * It exchanges the nodereferences and notifies the main application about the same after a succesful exchange
 */
package freenet.darknetconnector.NodeReferenceExchange;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;

import android.bluetooth.BluetoothSocket;
import android.os.Message;
import android.util.Log;

import freenet.darknetconnector.DarknetAppConnector.DarknetAppConnector;
import freenet.support.io.LineReadingInputStream;

public class ConnectionThread extends Thread {
	private static final String REQUEST_REFERENCE_EXCHANGE = "Request Reference Exchange";
	private static final String TAG = "ConnectionThread";
	private LineReadingInputStream input = null;
	private OutputStream output = null;
	private Socket socket;
	private BluetoothSocket bsocket;
	private boolean isBluetooth;
	public ConnectionThread(Socket socket) {
		Log.d("ConnectionThread","starting connection thread");
		this.socket = socket;
		try {
			if (socket!=null) {
				socket.setSoTimeout(1000);
				Log.d("ConnectionThread","set socket timeout to 1000");
			}
			else throw new SocketException();
		} catch (SocketException e1) {
			Log.e("ConnectionThread","Couldnot set timeout for socket");
		}
		InputStream is = null;
		try {
			is = new BufferedInputStream(socket.getInputStream(), 4096);
			input = new LineReadingInputStream(is);
			Log.d("ConnectionThread","got input stream ");
			output = socket.getOutputStream();
			Log.d("ConnectionThread","got output stream ");
		} catch (IOException e) {
			Log.e("ConnectionThread","Couldn't acquire input and output streams",e);
		}
		Log.d("ConnectionThread","connection started");
		isBluetooth = false;
	}
	public ConnectionThread(BluetoothSocket bsocket) {
		this.bsocket = bsocket;
		isBluetooth = true;
	}
	public void run() {
		if (isBluetooth) {
			try {
				InputStream is = new BufferedInputStream(bsocket.getInputStream(), 4096);
				input = new LineReadingInputStream(is);
				output = bsocket.getOutputStream();
			} catch (IOException e) {
				Log.e(ConnectionThread.TAG,"couldn't get input stream",e);
				BluetoothActivity.closeActivity();
			}
		}
		File myRefFile = new File(DarknetAppConnector.nodeRefFileName);
		File peerRefFile = new File(DarknetAppConnector.peerNodeRefsFileName);
		if (!peerRefFile.exists())
			try {
				peerRefFile.createNewFile();
			} catch (IOException e3) {
				e3.printStackTrace();
			} 
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(myRefFile));
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		}
		try {
			output.write((REQUEST_REFERENCE_EXCHANGE+'\n').getBytes("UTF-8"));
			if (input==null) throw new IOException();
			Random randomGenerator = new Random();
			String command = input.readLine(32768, 128, true);
			Log.d(ConnectionThread.TAG,"command -" + command);
			String line = null;
			String friendReference = "";
			String fromSocket = null;
			boolean doneReading = false;
			int rand = 0;
			int back = 0;
			
			if (command!=null && command.equals(REQUEST_REFERENCE_EXCHANGE)) {
				// Deciding which of the two mobiles should transfer first
				// Both of them sending and receiving at the same time is resulting in crashes in case of bluetooth in old mobiles
				while (rand == back) {
					rand = randomGenerator.nextInt(1000);
					output.write(((""+rand)+'\n').getBytes("UTF-8"));
					back = Integer.parseInt(input.readLine(32768, 128, true));
				}
				// Our mobile sends the complete reference first and then receives peer reference
				if (rand > back) {
					while ((line = br.readLine()) != null) {
						output.write((line+'\n').getBytes("UTF-8"));
					}
					while((fromSocket = input.readLine(32768, 128, true)) != null) {
						friendReference = friendReference.concat(fromSocket+'\n');
					    Log.d(ConnectionThread.TAG,"friendRef +---" +fromSocket);
						if (fromSocket.endsWith("End"))  {
							doneReading = true;
							break;
						}
					}
				}
				// Our mobile receives the peer reference first and then sends our reference
				else {
					while((fromSocket = input.readLine(32768, 128, true)) != null) {
						friendReference = friendReference.concat(fromSocket+'\n');
						if (fromSocket.endsWith("End"))  {
							doneReading = true;
							break;
						}
					}
					while ((line = br.readLine()) != null) {
						output.write((line+'\n').getBytes("UTF-8"));
					}
				}
			/*Not working on old mobiles in case of bluetooth
			   if (command!=null && command.equals(REQUEST_REFERENCE_EXCHANGE)) {
				while ((line = br.readLine()) != null) {
				    output.write((line+'\n').getBytes("UTF-8"));
				    Log.d("dumb","line +---" +line);
				    if (!doneReading) {
				    	if ((fromSocket = input.readLine(32768, 128, true)) != null) {
				    		friendReference = friendReference.concat(fromSocket+'\n');
				    		Log.d("dumb","fromSocket +---" +fromSocket);
				    		if (fromSocket.endsWith("End")) doneReading = true;
				    	}
				    }
				}
				if (!doneReading) {
					while((fromSocket = input.readLine(32768, 128, true)) != null) {
						friendReference = friendReference.concat(fromSocket+'\n');
						if (fromSocket.endsWith("End"))  {
							doneReading = true;
							break;
						}
					}
				}*/
				
				// Tell the base class about the successful exchange
				// Also, send the exchange in the message
				Message msg = new Message();
				msg.arg1 = DarknetAppConnector.MESSAGE_PEERS_UPDATED;
				msg.obj = friendReference;
				DarknetAppConnector.handler.sendMessage(msg);
			}
			else {
				throw new IOException();
			}
		} catch (IOException e) {
			Log.e("ConnectionThread","output error",e);
		}
		Log.d("ConnectionThread","connection running");
	}
	public void cancel() {
		Log.d("ConnectionThread","connection thread is being closed");
		if (input!=null)
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		if (output!=null)
			try {
				output.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		if (socket!=null)
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		if (bsocket!=null)
			try {
				bsocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		bsocket = null;
		socket = null;
		output = null;
		input = null;
		Log.d("ConnectionThread","connection closed");
	}
}

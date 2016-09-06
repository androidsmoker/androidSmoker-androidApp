package  com.kapapa.androidsmoker;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.json.JSONObject;

import java.net.InetSocketAddress;

/**
 * Exposes a WebSocket for web clients, formats the sensor data as JSON, passes through commands from the client
 */
public class SmokerWebSocket extends WebSocketServer {
	private static final String TAG = "AndroidSmoker";
	private AndroidSmokerActivity p = null;

	public SmokerWebSocket(AndroidSmokerActivity p) {
		this( new InetSocketAddress(9999));
		this.p = p;
	}

	public SmokerWebSocket(InetSocketAddress addr) {
		super(addr);
	}


	@Override
	public void onClose(WebSocket ws, int arg1, String arg2, boolean arg3) {
		Log.d(TAG, "close socket.");
	}

	@Override
	public void onError(WebSocket arg0, Exception arg1) {
	}

	@Override
	public void onMessage(WebSocket arg0, String str) {
		Log.d(TAG, str);
		try {
			Log.i(TAG, "received cmd '"+str+"'");
			p.sendCommand(str);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onOpen(WebSocket ws, ClientHandshake arg1)
    {
		Log.d(TAG, "open socket.");
	}

	public void write(ArduinoData ad) {
		try {

			JSONObject data = new JSONObject();
			data.put("ambientTemp", ad.ambient);
			data.put("pitTemp", ad.pit);
			data.put("probe1Temp", ad.food1);
			data.put("probe2Temp", ad.food2);

//			data.put("probe1Temp", "OFF");
//			data.put("probe2Temp", "OFF");

			data.put("setpoint", ad.setpoint);
			data.put("fanSpeed", ad.fanSpeed);
			data.put("windSpeed", ad.windSpeed);
			data.put("control", ad.control);

			JSONObject json = new JSONObject();
			json.put("type", "sensorUpdate");
			json.put("data", data);

			writeJSON(json.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void write(PIDData pd) {
		try {

			JSONObject data = new JSONObject();
			data.put("output", pd.output);
			data.put("input", pd.input);
			data.put("setpoint", pd.setpoint);
			data.put("error", pd.error);
			data.put("iterm", pd.iterm);
			data.put("dinput", pd.dinput);

			JSONObject json = new JSONObject();
			json.put("type", "pidUpdate");
			json.put("data", data);

			writeJSON(json.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void write(String kp, String ki, String kd) {
		try {

			JSONObject data = new JSONObject();
			data.put("kp", kp);
			data.put("ki", ki);
			data.put("kd", kd);

			JSONObject json = new JSONObject();
			json.put("type", "autoTune");
			json.put("data", data);

			writeJSON(json.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void writeJSON(String json) throws Exception {
		for (WebSocket socket : connections()) {
			socket.send(json);
		}
	}


}

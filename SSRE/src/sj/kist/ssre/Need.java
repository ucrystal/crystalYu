package sj.kist.ssre;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class Need {

	private SharedPreferences prefs = null;
	private SharedPreferences.Editor editor = null;

	public Need(Context context) {
		prefs = context.getSharedPreferences("Need", Context.MODE_PRIVATE
				| Context.MODE_MULTI_PROCESS);

		editor = prefs.edit();
	}

	public void setData(final String key, final int value) {
		editor.putInt(key, value);
		editor.commit();
	}

	public void setData(final String[] keys, final int[] values) {
		for (int i = 0; i < keys.length; i++) {
			editor.putInt(keys[i], values[i]);
		}
		editor.commit();
	}

	public int getData(final String key) {
		return prefs.getInt(key, '0');
	}

	public void getAllValue() {
		Map<String, ?> keys = prefs.getAll();
		for (Map.Entry<String, ?> entry : keys.entrySet()) {
			Log.d("map values", entry.getKey() + ","
					+ entry.getValue().toString());
		}

		Collection<?> col = prefs.getAll().values();
		Iterator<?> it = col.iterator();

		while (it.hasNext()) {
			String msg = (String) it.next();
			Log.d("Result", msg);
		}

		int size = prefs.getAll().size();
	}
	
	synchronized public void updateData(final String key, final String operator, final int count) {
		int currentValue = getData(key);
		int newValue = 0;
		
		if(operator=="add") {
			newValue = currentValue + count;
		} else if (operator=="abstract") {
			newValue = currentValue - count;	
		} else {
			return;
		}
		prefs.edit().putInt(key, newValue).apply();
	}
	
	synchronized public void updateData(final String key, final int newValue) {
		prefs.edit().putInt(key, newValue).apply();
	}

}

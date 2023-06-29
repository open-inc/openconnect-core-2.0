package de.openinc.openconnect.plcconnector;

import de.openinc.model.data.OpenWareDataItem;

public interface PLCSubscriptionCallback {

	public void onNewData(OpenWareDataItem previous, OpenWareDataItem newItem);
}

/*  Copyright (C) 2015-2024 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Daniel Dakhno, José Rebelo, Julien Pivotto, Kasha, Sebastian Kranz,
    Steffen Liebergeld

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.LoyaltyCard;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceMusic;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Contact;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Reminder;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock;

/**
 * Wraps another device support instance and supports busy-checking and throttling of events.
 */
public class ServiceDeviceSupport implements DeviceSupport {
    public static enum Flags {
        THROTTLING,
        BUSY_CHECKING,
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServiceDeviceSupport.class);

    private static final long THROTTLING_THRESHOLD = 1000; // throttle multiple events in between one second
    private final DeviceSupport delegate;

    private long lastNotificationTime = 0;
    private String lastNotificationKind;
    private final EnumSet<Flags> flags;

    public ServiceDeviceSupport(DeviceSupport delegate, EnumSet<Flags> flags) {
        this.delegate = delegate;
        this.flags = flags;
    }

    @Override
    public void setContext(GBDevice gbDevice, BluetoothAdapter btAdapter, Context context) {
        delegate.setContext(gbDevice, btAdapter, context);
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean connectFirstTime() {
        return delegate.connectFirstTime();
    }

    @Override
    public boolean connect() {
        return delegate.connect();
    }

    @Override
    public void setAutoReconnect(boolean enable) {
        delegate.setAutoReconnect(enable);
    }

    @Override
    public boolean getAutoReconnect() {
        return delegate.getAutoReconnect();
    }

    @Override
    public void setScanReconnect(boolean enable) {
        delegate.setScanReconnect(enable);
    }

    @Override
    public boolean getScanReconnect(){
        return delegate.getScanReconnect();
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public GBDevice getDevice() {
        return delegate.getDevice();
    }

    @Override
    public BluetoothAdapter getBluetoothAdapter() {
        return delegate.getBluetoothAdapter();
    }

    @Override
    public Context getContext() {
        return delegate.getContext();
    }

    @Override
    public String customStringFilter(String inputString) {
        return delegate.customStringFilter(inputString);
    }

    @Override
    public boolean useAutoConnect() {
        return delegate.useAutoConnect();
    }

    private boolean checkBusy(String notificationKind) {
        if (!flags.contains(Flags.BUSY_CHECKING)) {
            return false;
        }
        if (getDevice().isBusy()) {
            LOG.info("Ignoring " + notificationKind + " because we're busy with " + getDevice().getBusyTask());
            return true;
        }
        return false;
    }

    private boolean checkThrottle(String notificationKind) {
        if (!flags.contains(Flags.THROTTLING)) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastNotificationTime) < THROTTLING_THRESHOLD) {
            if (notificationKind != null && notificationKind.equals(lastNotificationKind)) {
                LOG.info("Ignoring " + notificationKind + " because of throttling threshold reached");
                return true;
            }
        }
        lastNotificationTime = currentTime;
        lastNotificationKind = notificationKind;
        return false;
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        if (checkBusy("generic notification") || checkThrottle("generic notification")) {
            return;
        }
        delegate.onNotification(notificationSpec);
    }

    @Override
    public void onDeleteNotification(int id) {
        delegate.onDeleteNotification(id);
    }

    @Override
    public void onSetTime() {
        if (checkBusy("set time") || checkThrottle("set time")) {
            return;
        }
        delegate.onSetTime();
    }

    // No throttling for the other events

    @Override
    public void onSetCallState(CallSpec callSpec) {
        if (checkBusy("set call state")) {
            return;
        }
        delegate.onSetCallState(callSpec);
    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        if (checkBusy("set canned messages")) {
            return;
        }
        delegate.onSetCannedMessages(cannedMessagesSpec);
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
        if (checkBusy("set music state")) {
            return;
        }
        delegate.onSetMusicState(stateSpec);
    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        if (checkBusy("set music info")) {
            return;
        }
        delegate.onSetMusicInfo(musicSpec);
    }

    @Override
    public void onSetPhoneVolume(float volume) {
        if (checkBusy("set phone volume")) {
            return;
        }
        delegate.onSetPhoneVolume(volume);
    }

    @Override
    public void onChangePhoneSilentMode(final int ringerMode) {
        if (checkBusy("change phone silent mode")) {
            return;
        }
        delegate.onChangePhoneSilentMode(ringerMode);
    }

    @Override
    public void onSetNavigationInfo(NavigationInfoSpec navigationInfoSpec) {
        if (checkBusy("set navigation info")) {
            return;
        }
        delegate.onSetNavigationInfo(navigationInfoSpec);
    }

    @Override
    public void onInstallApp(Uri uri) {
        if (checkBusy("install app")) {
            return;
        }
        delegate.onInstallApp(uri);
    }

    @Override
    public void onAppInfoReq() {
        if (checkBusy("app info request")) {
            return;
        }
        delegate.onAppInfoReq();
    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {
        if (checkBusy("app start")) {
            return;
        }
        delegate.onAppStart(uuid, start);
    }

    @Override
    public void onAppDownload(UUID uuid) {
        if (checkBusy("app download")) {
            return;
        }
        delegate.onAppDownload(uuid);
    }

    @Override
    public void onAppDelete(UUID uuid) {
        if (checkBusy("app delete")) {
            return;
        }
        delegate.onAppDelete(uuid);
    }

    @Override
    public void onAppConfiguration(UUID uuid, String config, Integer id) {
        if (checkBusy("app configuration")) {
            return;
        }
        delegate.onAppConfiguration(uuid, config, id);
    }

    @Override
    public void onAppReorder(UUID[] uuids) {
        if (checkBusy("app reorder")) {
            return;
        }
        delegate.onAppReorder(uuids);
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        if (checkBusy("fetch activity data")) {
            return;
        }
        delegate.onFetchRecordedData(dataTypes);
    }

    @Override
    public void onReset(int flags) {
        if (checkBusy("reset")) {
            return;
        }
        delegate.onReset(flags);
    }

    @Override
    public void onHeartRateTest() {
        if (checkBusy("heartrate")) {
            return;
        }
        delegate.onHeartRateTest();
    }

    @Override
    public void onFindDevice(boolean start) {
        if (checkBusy("find device")) {
            return;
        }
        delegate.onFindDevice(start);
    }

    @Override
    public void onFindPhone(final boolean start) {
        if (checkBusy("phone found")) {
            return;
        }
        delegate.onFindPhone(start);
    }

    @Override
    public void onSetConstantVibration(int intensity) {
        if (checkBusy("set constant vibration")) {
            return;
        }
        delegate.onSetConstantVibration(intensity);
    }

    @Override
    public void onScreenshotReq() {
        if (checkBusy("request screenshot")) {
            return;
        }
        delegate.onScreenshotReq();
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        if (checkBusy("set alarms")) {
            return;
        }
        delegate.onSetAlarms(alarms);
    }

    @Override
    public void onSetReminders(ArrayList<? extends Reminder> reminders) {
        if (checkBusy("set reminders")) {
            return;
        }
        delegate.onSetReminders(reminders);
    }

    @Override
    public void onSetContacts(ArrayList<? extends Contact> contacts) {
        if (checkBusy("set contacts")) {
            return;
        }
        delegate.onSetContacts(contacts);
    }

    public void onSetLoyaltyCards(final ArrayList<LoyaltyCard> cards) {
        if (checkBusy("set loyalty cards")) {
            return;
        }
        delegate.onSetLoyaltyCards(cards);
    }

    @Override
    public void onSetWorldClocks(ArrayList<? extends WorldClock> clocks) {
        if (checkBusy("set world clocks")) {
            return;
        }
        delegate.onSetWorldClocks(clocks);
    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {
        if (checkBusy("enable realtime steps: " + enable)) {
            return;
        }
        delegate.onEnableRealtimeSteps(enable);
    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {
        if (checkBusy("enable heart rate sleep support: " + enable)) {
            return;
        }
        delegate.onEnableHeartRateSleepSupport(enable);
    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {
        if (checkBusy("set heart rate measurement interval: " + seconds + "s")) {
            return;
        }
        delegate.onSetHeartRateMeasurementInterval(seconds);
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {
        if (checkBusy("enable realtime heart rate measurement: " + enable)) {
            return;
        }
        delegate.onEnableRealtimeHeartRateMeasurement(enable);
    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {
        if (checkBusy("add calendar event")) {
            return;
        }
        delegate.onAddCalendarEvent(calendarEventSpec);
    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {
        if (checkBusy("delete calendar event")) {
            return;
        }
        delegate.onDeleteCalendarEvent(type, id);
    }

    @Override
    public void onSendConfiguration(String config) {
        if (checkBusy("send configuration: " + config)) {
            return;
        }
        delegate.onSendConfiguration(config);
    }

    @Override
    public void onReadConfiguration(String config) {
        if (checkBusy("read configuration: " + config)) {
            return;
        }
        delegate.onReadConfiguration(config);
    }

    @Override
    public void onTestNewFunction() {
        if (checkBusy("test new function event")) {
            return;
        }
        delegate.onTestNewFunction();
    }

    @Override
    public void onSendWeather(ArrayList<WeatherSpec> weatherSpecs) {
        if (checkBusy("send weather events")) {
            return;
        }
        delegate.onSendWeather(weatherSpecs);
    }

    @Override
    public void onSetFmFrequency(float frequency) {
        if (checkBusy("set frequency event")) {
            return;
        }
        delegate.onSetFmFrequency(frequency);
    }

    @Override
    public void onSetLedColor(int color) {
        if (checkBusy("set led color event")) {
            return;
        }
        delegate.onSetLedColor(color);
    }

    @Override
    public void onPowerOff() {
        if (checkBusy("power off event")) {
            return;
        }
        delegate.onPowerOff();
    }

    @Override
    public void onSetGpsLocation(Location location) {
        if (checkBusy("set gps location")) {
            return;
        }
        delegate.onSetGpsLocation(location);
    }

    @Override
    public void onSleepAsAndroidAction(String action, Bundle extras) {
        if (checkBusy("sleep as android")) {
            return;
        }
        delegate.onSleepAsAndroidAction(action, extras);
    }

    @Override
    public void onCameraStatusChange(GBDeviceEventCameraRemote.Event event, String filename) {
        if (checkBusy("camera status")) {
            return;
        }
        delegate.onCameraStatusChange(event, filename);
    }

    @Override
    public void onMusicListReq() {
        if (checkBusy("music list request")) {
            return;
        }
        delegate.onMusicListReq();
    }

    @Override
    public void onMusicOperation(int operation, int playlistIndex, String playlistName, ArrayList<Integer> musicIds) {
        if (checkBusy("music operation")) {
            return;
        }
        delegate.onMusicOperation(operation, playlistIndex, playlistName, musicIds);
    }
}

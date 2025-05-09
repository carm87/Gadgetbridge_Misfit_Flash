/*  Copyright (C) 2015-2024 Alberto, Andreas Böhler, Andreas Shimokawa,
    Arjan Schrijver, Carsten Pfeiffer, criogenic, Daniel Dakhno, Daniele Gobbetti,
    Davis Mosenkovs, Frank Slezak, Gabriele Monaco, Gordon Williams, ivanovlev,
    José Rebelo, Julien Pivotto, Kasha, mvn23, Petr Vaněk, Roi Greenberg,
    Sebastian Kranz, Steffen Liebergeld

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
package nodomain.freeyourgadget.gadgetbridge.impl;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.LoyaltyCard;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Contact;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.Reminder;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceCommunicationService;
import nodomain.freeyourgadget.gadgetbridge.util.RtlUtils;

import static nodomain.freeyourgadget.gadgetbridge.util.JavaExtensions.coalesce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GBDeviceService implements DeviceService {
    protected final Context mContext;
    private final GBDevice mDevice;
    private final Class<? extends Service> mServiceClass;
    public static final String[] transliterationExtras = new String[]{
            EXTRA_NOTIFICATION_SENDER,
            EXTRA_NOTIFICATION_SUBJECT,
            EXTRA_NOTIFICATION_TITLE,
            EXTRA_NOTIFICATION_BODY,
            EXTRA_NOTIFICATION_SOURCENAME,
            EXTRA_CALL_DISPLAYNAME,
            EXTRA_CALL_SOURCENAME,
            EXTRA_MUSIC_ARTIST,
            EXTRA_MUSIC_ALBUM,
            EXTRA_MUSIC_TRACK,
            EXTRA_CALENDAREVENT_TITLE,
            EXTRA_CALENDAREVENT_DESCRIPTION,
            EXTRA_CALENDAREVENT_LOCATION,
            EXTRA_CALENDAREVENT_CALNAME,
    };
    private static final Logger LOG = LoggerFactory.getLogger(GBDeviceService.class);

    public GBDeviceService(Context context) {
        this(context, null);
    }

    public GBDeviceService(Context context, GBDevice device) {
        mContext = context;
        mDevice = device;
        mServiceClass = DeviceCommunicationService.class;
    }

    @Override
    public DeviceService forDevice(final GBDevice device) {
        return new GBDeviceService(mContext, device);
    }

    public GBDevice getDevice() {
        return mDevice;
    }

    protected Intent createIntent() {
        return new Intent(mContext, mServiceClass);
    }

    protected void invokeService(Intent intent) {

        if (RtlUtils.rtlSupport()) {
            for (String extra : transliterationExtras) {
                if (intent.hasExtra(extra)) {
                    intent.putExtra(extra, RtlUtils.fixRtl(intent.getStringExtra(extra)));
                }
            }
        }

        if (mDevice != null) {
            intent.putExtra(GBDevice.EXTRA_DEVICE, mDevice);
        }
        try {
            mContext.startService(intent);
        } catch (IllegalStateException e) {
            LOG.error("IllegalStateException during startService ("+intent.getAction()+")");
        }
    }

    protected void stopService(Intent intent) {
        mContext.stopService(intent);
    }

    @Override
    public void connect() {
        connect(false);
    }

    @Override
    public void connect(boolean firstTime) {
        Intent intent = createIntent().setAction(ACTION_CONNECT)
                .putExtra(EXTRA_CONNECT_FIRST_TIME, firstTime);
        invokeService(intent);
    }

    @Override
    public void disconnect() {
        Intent intent = createIntent().setAction(ACTION_DISCONNECT);
        invokeService(intent);
    }

    @Override
    public void quit() {
        Intent intent = createIntent();
        stopService(intent);
    }

    @Override
    public void requestDeviceInfo() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_DEVICEINFO);
        invokeService(intent);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        String messagePrivacyMode = GBApplication.getPrefs().getString("pref_message_privacy_mode",
                GBApplication.getContext().getString(R.string.p_message_privacy_mode_off));
        boolean hideMessageDetails = messagePrivacyMode.equals(GBApplication.getContext().getString(R.string.p_message_privacy_mode_complete));
        boolean hideMessageBodyOnly = messagePrivacyMode.equals(GBApplication.getContext().getString(R.string.p_message_privacy_mode_bodyonly));

        Intent intent = createIntent().setAction(ACTION_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_FLAGS, notificationSpec.flags)
                .putExtra(EXTRA_NOTIFICATION_PHONENUMBER, hideMessageDetails ? null : notificationSpec.phoneNumber)
                .putExtra(EXTRA_NOTIFICATION_SENDER, hideMessageDetails ? null : coalesce(notificationSpec.sender, getContactDisplayNameByNumber(notificationSpec.phoneNumber)))
                .putExtra(EXTRA_NOTIFICATION_SUBJECT, hideMessageDetails ? null : notificationSpec.subject)
                .putExtra(EXTRA_NOTIFICATION_TITLE, hideMessageDetails ? null : notificationSpec.title)
                .putExtra(EXTRA_NOTIFICATION_BODY, hideMessageDetails || hideMessageBodyOnly ? null : notificationSpec.body)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationSpec.getId())
                .putExtra(EXTRA_NOTIFICATION_KEY, notificationSpec.key)
                .putExtra(EXTRA_NOTIFICATION_TYPE, notificationSpec.type)
                .putExtra(EXTRA_NOTIFICATION_ACTIONS, notificationSpec.attachedActions)
                .putExtra(EXTRA_NOTIFICATION_SOURCENAME, notificationSpec.sourceName)
                .putExtra(EXTRA_NOTIFICATION_PEBBLE_COLOR, notificationSpec.pebbleColor)
                .putExtra(EXTRA_NOTIFICATION_SOURCEAPPID, notificationSpec.sourceAppId)
                .putExtra(EXTRA_NOTIFICATION_ICONID, notificationSpec.iconId)
                .putExtra(NOTIFICATION_PICTURE_PATH, notificationSpec.picturePath)
                .putExtra(EXTRA_NOTIFICATION_DNDSUPPRESSED, notificationSpec.dndSuppressed);
        invokeService(intent);
    }

    @Override
    public void onDeleteNotification(int id) {
        Intent intent = createIntent().setAction(ACTION_DELETE_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_ID, id);
        invokeService(intent);

    }

    @Override
    public void onSetTime() {
        Intent intent = createIntent().setAction(ACTION_SETTIME);
        invokeService(intent);
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        Intent intent = createIntent().setAction(ACTION_SET_ALARMS)
                .putExtra(EXTRA_ALARMS, alarms);
        invokeService(intent);
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        Context context = GBApplication.getContext();
        String currentPrivacyMode = GBApplication.getPrefs().getString("pref_call_privacy_mode", GBApplication.getContext().getString(R.string.p_call_privacy_mode_off));
        if (currentPrivacyMode.equals(context.getString(R.string.p_call_privacy_mode_name))) {
            callSpec.name = callSpec.number;
        } else if (currentPrivacyMode.equals(context.getString(R.string.p_call_privacy_mode_complete))) {
            callSpec.number = null;
            callSpec.name = null;
        } else if (currentPrivacyMode.equals(context.getString(R.string.p_call_privacy_mode_number))){
            callSpec.name = coalesce(callSpec.name, getContactDisplayNameByNumber(callSpec.number));
            if (callSpec.name != null && !callSpec.name.equals(callSpec.number)) {
                callSpec.number = null;
            }
        } else {
            callSpec.name = coalesce(callSpec.name, getContactDisplayNameByNumber(callSpec.number));
        }

        Intent intent = createIntent().setAction(ACTION_CALLSTATE)
                .putExtra(EXTRA_CALL_PHONENUMBER, callSpec.number)
                .putExtra(EXTRA_CALL_DISPLAYNAME, callSpec.name)
                .putExtra(EXTRA_CALL_SOURCENAME, callSpec.sourceName)
                .putExtra(EXTRA_CALL_SOURCEAPPID, callSpec.sourceAppId)
                .putExtra(EXTRA_CALL_COMMAND, callSpec.command)
                .putExtra(EXTRA_CALL_DNDSUPPRESSED, callSpec.dndSuppressed);
        invokeService(intent);
    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        Intent intent = createIntent().setAction(ACTION_SETCANNEDMESSAGES)
                .putExtra(EXTRA_CANNEDMESSAGES_TYPE, cannedMessagesSpec.type)
                .putExtra(EXTRA_CANNEDMESSAGES, cannedMessagesSpec.cannedMessages);
        invokeService(intent);
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {
        Intent intent = createIntent().setAction(ACTION_SETMUSICSTATE)
                .putExtra(EXTRA_MUSIC_REPEAT, stateSpec.repeat)
                .putExtra(EXTRA_MUSIC_RATE, stateSpec.playRate)
                .putExtra(EXTRA_MUSIC_STATE, stateSpec.state)
                .putExtra(EXTRA_MUSIC_SHUFFLE, stateSpec.shuffle)
                .putExtra(EXTRA_MUSIC_POSITION, stateSpec.position);
        invokeService(intent);
    }

    @Override
    public void onSetPhoneVolume(final float volume) {
        Intent intent = createIntent().setAction(ACTION_SET_PHONE_VOLUME)
                .putExtra(EXTRA_PHONE_VOLUME, volume);
        invokeService(intent);
    }

    @Override
    public void onChangePhoneSilentMode(int ringerMode) {
        Intent intent = createIntent().setAction(ACTION_SET_PHONE_SILENT_MODE)
                .putExtra(EXTRA_PHONE_RINGER_MODE, ringerMode);
        invokeService(intent);
    }

    @Override
    public void onSetReminders(ArrayList<? extends Reminder> reminders) {
        Intent intent = createIntent().setAction(ACTION_SET_REMINDERS)
                .putExtra(EXTRA_REMINDERS, reminders);
        invokeService(intent);
    }

    @Override
    public void onSetLoyaltyCards(final ArrayList<LoyaltyCard> cards) {
        final Intent intent = createIntent().setAction(ACTION_SET_LOYALTY_CARDS)
                .putExtra(EXTRA_LOYALTY_CARDS, cards);
        invokeService(intent);
    }

    @Override
    public void onSetWorldClocks(ArrayList<? extends WorldClock> clocks) {
        Intent intent = createIntent().setAction(ACTION_SET_WORLD_CLOCKS)
                .putExtra(EXTRA_WORLD_CLOCKS, clocks);
        invokeService(intent);
    }

    @Override
    public void onSetContacts(ArrayList<? extends Contact> contacts) {
        Intent intent = createIntent().setAction(ACTION_SET_CONTACTS)
                .putExtra(EXTRA_CONTACTS, contacts);
        invokeService(intent);
    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        Intent intent = createIntent().setAction(ACTION_SETMUSICINFO)
                .putExtra(EXTRA_MUSIC_ARTIST, musicSpec.artist)
                .putExtra(EXTRA_MUSIC_ALBUM, musicSpec.album)
                .putExtra(EXTRA_MUSIC_TRACK, musicSpec.track)
                .putExtra(EXTRA_MUSIC_DURATION, musicSpec.duration)
                .putExtra(EXTRA_MUSIC_TRACKCOUNT, musicSpec.trackCount)
                .putExtra(EXTRA_MUSIC_TRACKNR, musicSpec.trackNr);
        invokeService(intent);
    }

    @Override
    public void onSetNavigationInfo(NavigationInfoSpec navigationInfoSpec) {
        Intent intent = createIntent().setAction(ACTION_SETNAVIGATIONINFO)
                .putExtra(EXTRA_NAVIGATION_INSTRUCTION, navigationInfoSpec.instruction)
                .putExtra(EXTRA_NAVIGATION_NEXT_ACTION, navigationInfoSpec.nextAction)
                .putExtra(EXTRA_NAVIGATION_DISTANCE_TO_TURN, navigationInfoSpec.distanceToTurn)
                .putExtra(EXTRA_NAVIGATION_ETA, navigationInfoSpec.ETA);
        invokeService(intent);
    }

    @Override
    public void onInstallApp(Uri uri) {
        Intent intent = createIntent().setAction(ACTION_INSTALL)
                .putExtra(EXTRA_URI, uri);
        invokeService(intent);
    }

    @Override
    public void onAppInfoReq() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_APPINFO);
        invokeService(intent);
    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {
        Intent intent = createIntent().setAction(ACTION_STARTAPP)
                .putExtra(EXTRA_APP_UUID, uuid)
                .putExtra(EXTRA_APP_START, start);
        invokeService(intent);
    }

    @Override
    public void onAppDownload(UUID uuid) {
        Intent intent = createIntent().setAction(ACTION_DOWNLOADAPP)
                .putExtra(EXTRA_APP_UUID, uuid);
        invokeService(intent);
    }

    @Override
    public void onAppDelete(UUID uuid) {
        Intent intent = createIntent().setAction(ACTION_DELETEAPP)
                .putExtra(EXTRA_APP_UUID, uuid);
        invokeService(intent);
    }

    @Override
    public void onAppConfiguration(UUID uuid, String config, Integer id) {
        Intent intent = createIntent().setAction(ACTION_APP_CONFIGURE)
                .putExtra(EXTRA_APP_UUID, uuid)
                .putExtra(EXTRA_APP_CONFIG, config);

        if (id != null) {
            intent.putExtra(EXTRA_APP_CONFIG_ID, id);
        }
        invokeService(intent);
    }

    @Override
    public void onAppReorder(UUID[] uuids) {
        Intent intent = createIntent().setAction(ACTION_APP_REORDER)
                .putExtra(EXTRA_APP_UUID, uuids);
        invokeService(intent);
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        Intent intent = createIntent().setAction(ACTION_FETCH_RECORDED_DATA)
                .putExtra(EXTRA_RECORDED_DATA_TYPES, dataTypes);
        invokeService(intent);
    }

    @Override
    public void onReset(int flags) {
        Intent intent = createIntent().setAction(ACTION_RESET)
                .putExtra(EXTRA_RESET_FLAGS, flags);
        invokeService(intent);
    }

    @Override
    public void onHeartRateTest() {
        Intent intent = createIntent().setAction(ACTION_HEARTRATE_TEST);
        invokeService(intent);
    }

    @Override
    public void onFindDevice(boolean start) {
        Intent intent = createIntent().setAction(ACTION_FIND_DEVICE)
                .putExtra(EXTRA_FIND_START, start);
        invokeService(intent);
    }

    @Override
    public void onFindPhone(final boolean start) {
        Intent intent = createIntent().setAction(ACTION_PHONE_FOUND)
                        .putExtra(EXTRA_FIND_START, start);
        invokeService(intent);
    }

    @Override
    public void onSetConstantVibration(int intensity) {
        Intent intent = createIntent().setAction(ACTION_SET_CONSTANT_VIBRATION)
                .putExtra(EXTRA_VIBRATION_INTENSITY, intensity);
        invokeService(intent);
    }

    @Override
    public void onScreenshotReq() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_SCREENSHOT);
        invokeService(intent);
    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {
        Intent intent = createIntent().setAction(ACTION_ENABLE_REALTIME_STEPS)
                .putExtra(EXTRA_BOOLEAN_ENABLE, enable);
        invokeService(intent);
    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {
        Intent intent = createIntent().setAction(ACTION_ENABLE_HEARTRATE_SLEEP_SUPPORT)
                .putExtra(EXTRA_BOOLEAN_ENABLE, enable);
        invokeService(intent);
    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {
        Intent intent = createIntent().setAction(ACTION_SET_HEARTRATE_MEASUREMENT_INTERVAL)
                .putExtra(EXTRA_INTERVAL_SECONDS, seconds);
        invokeService(intent);
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {
        Intent intent = createIntent().setAction(ACTION_ENABLE_REALTIME_HEARTRATE_MEASUREMENT)
                .putExtra(EXTRA_BOOLEAN_ENABLE, enable);
        invokeService(intent);
    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {
        Intent intent = createIntent().setAction(ACTION_ADD_CALENDAREVENT)
                .putExtra(EXTRA_CALENDAREVENT_ID, calendarEventSpec.id)
                .putExtra(EXTRA_CALENDAREVENT_TYPE, calendarEventSpec.type)
                .putExtra(EXTRA_CALENDAREVENT_TIMESTAMP, calendarEventSpec.timestamp)
                .putExtra(EXTRA_CALENDAREVENT_DURATION, calendarEventSpec.durationInSeconds)
                .putExtra(EXTRA_CALENDAREVENT_ALLDAY, calendarEventSpec.allDay)
                .putExtra(EXTRA_CALENDAREVENT_REMINDERS, calendarEventSpec.reminders)
                .putExtra(EXTRA_CALENDAREVENT_TITLE, calendarEventSpec.title)
                .putExtra(EXTRA_CALENDAREVENT_DESCRIPTION, calendarEventSpec.description)
                .putExtra(EXTRA_CALENDAREVENT_CALNAME, calendarEventSpec.calName)
                .putExtra(EXTRA_CALENDAREVENT_COLOR, calendarEventSpec.color)
                .putExtra(EXTRA_CALENDAREVENT_LOCATION, calendarEventSpec.location);
        invokeService(intent);
    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {
        Intent intent = createIntent().setAction(ACTION_DELETE_CALENDAREVENT)
                .putExtra(EXTRA_CALENDAREVENT_TYPE, type)
                .putExtra(EXTRA_CALENDAREVENT_ID, id);
        invokeService(intent);
    }

    @Override
    public void onSendConfiguration(String config) {
        Intent intent = createIntent().setAction(ACTION_SEND_CONFIGURATION)
                .putExtra(EXTRA_CONFIG, config);
        invokeService(intent);
    }

    @Override
    public void onReadConfiguration(String config) {
        Intent intent = createIntent().setAction(ACTION_READ_CONFIGURATION)
                .putExtra(EXTRA_CONFIG, config);
        invokeService(intent);
    }

    @Override
    public void onTestNewFunction() {
        Intent intent = createIntent().setAction(ACTION_TEST_NEW_FUNCTION);
        invokeService(intent);
    }

    @Override
    public void onSendWeather(ArrayList<WeatherSpec> weatherSpecs) {
        Intent intent = createIntent().setAction(ACTION_SEND_WEATHER)
                .putExtra(EXTRA_WEATHER, weatherSpecs);
        invokeService(intent);
    }

    /**
     * Returns contact DisplayName by call number
     *
     * @param number contact number
     * @return contact DisplayName, if found it
     */
    private String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(number));

        String name = number;

        if (number == null || number.equals("")) {
            return name;
        }

        try (Cursor contactLookup = mContext.getContentResolver().query(uri, null, null, null, null)) {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
            }
        } catch (SecurityException e) {
            // ignore, just return name below
        }

        return name;
    }

    @Override
    public void onSetFmFrequency(float frequency) {
        Intent intent = createIntent().setAction(ACTION_SET_FM_FREQUENCY)
                .putExtra(EXTRA_FM_FREQUENCY, frequency);
        invokeService(intent);
    }

    @Override
    public void onSetLedColor(int color) {
        Intent intent = createIntent().setAction(ACTION_SET_LED_COLOR)
                .putExtra(EXTRA_LED_COLOR, color);
        invokeService(intent);
    }

    @Override
    public void onPowerOff() {
        Intent intent = createIntent().setAction(ACTION_POWER_OFF);
        invokeService(intent);
    }

    @Override
    public void onSetGpsLocation(Location location) {
        Intent intent = createIntent().setAction(ACTION_SET_GPS_LOCATION);
        intent.putExtra(EXTRA_GPS_LOCATION, location);
        invokeService(intent);
    }

    @Override
    public void onSleepAsAndroidAction(String action, Bundle extras) {
        Intent intent = createIntent().setAction(ACTION_SLEEP_AS_ANDROID);
        intent.putExtra(EXTRA_SLEEP_AS_ANDROID_ACTION, action);
        if (extras != null) {
            intent.putExtras(extras);
        }
        invokeService(intent);
    }

    @Override
    public void onCameraStatusChange(GBDeviceEventCameraRemote.Event event, String filename) {
        Intent intent = createIntent().setAction(ACTION_CAMERA_STATUS_CHANGE);
        intent.putExtra(EXTRA_CAMERA_EVENT, GBDeviceEventCameraRemote.eventToInt(event));
        if (event == GBDeviceEventCameraRemote.Event.TAKE_PICTURE)
            intent.putExtra(EXTRA_CAMERA_FILENAME, filename);
        invokeService(intent);
    }

    @Override
    public void onMusicListReq() {
        Intent intent = createIntent().setAction(ACTION_REQUEST_MUSIC_LIST);
        invokeService(intent);
    }

    @Override
    public void onMusicOperation(int operation, int playlistIndex, String playlistName, ArrayList<Integer> musicIds) {
        Intent intent = createIntent().setAction(ACTION_REQUEST_MUSIC_OPERATION);
        intent.putExtra("operation", operation);
        intent.putExtra("playlistIndex", playlistIndex);
        intent.putExtra("playlistName", playlistName);
        intent.putExtra("musicIds", musicIds);
        invokeService(intent);
    }
}

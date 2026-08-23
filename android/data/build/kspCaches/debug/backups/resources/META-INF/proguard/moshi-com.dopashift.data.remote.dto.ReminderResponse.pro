-if class com.dopashift.data.remote.dto.ReminderResponse
-keepnames class com.dopashift.data.remote.dto.ReminderResponse
-if class com.dopashift.data.remote.dto.ReminderResponse
-keep class com.dopashift.data.remote.dto.ReminderResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

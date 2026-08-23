-if class com.dopashift.data.remote.dto.ServerTimestamp
-keepnames class com.dopashift.data.remote.dto.ServerTimestamp
-if class com.dopashift.data.remote.dto.ServerTimestamp
-keep class com.dopashift.data.remote.dto.ServerTimestampJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

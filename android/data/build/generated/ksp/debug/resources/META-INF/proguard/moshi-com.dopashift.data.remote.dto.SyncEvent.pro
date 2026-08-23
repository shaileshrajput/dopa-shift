-if class com.dopashift.data.remote.dto.SyncEvent
-keepnames class com.dopashift.data.remote.dto.SyncEvent
-if class com.dopashift.data.remote.dto.SyncEvent
-keep class com.dopashift.data.remote.dto.SyncEventJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

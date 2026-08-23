-if class com.dopashift.data.remote.dto.SyncPushRequest
-keepnames class com.dopashift.data.remote.dto.SyncPushRequest
-if class com.dopashift.data.remote.dto.SyncPushRequest
-keep class com.dopashift.data.remote.dto.SyncPushRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

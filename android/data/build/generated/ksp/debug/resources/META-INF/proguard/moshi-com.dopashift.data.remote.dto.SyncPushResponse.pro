-if class com.dopashift.data.remote.dto.SyncPushResponse
-keepnames class com.dopashift.data.remote.dto.SyncPushResponse
-if class com.dopashift.data.remote.dto.SyncPushResponse
-keep class com.dopashift.data.remote.dto.SyncPushResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.dopashift.data.remote.dto.SyncPushResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.dopashift.data.remote.dto.SyncPushResponse
-keepclassmembers class com.dopashift.data.remote.dto.SyncPushResponse {
    public synthetic <init>(java.util.List,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}

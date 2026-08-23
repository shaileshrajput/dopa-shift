-if class com.dopashift.data.remote.dto.CreateReminderRequest
-keepnames class com.dopashift.data.remote.dto.CreateReminderRequest
-if class com.dopashift.data.remote.dto.CreateReminderRequest
-keep class com.dopashift.data.remote.dto.CreateReminderRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.dopashift.data.remote.dto.CreateReminderRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.dopashift.data.remote.dto.CreateReminderRequest
-keepclassmembers class com.dopashift.data.remote.dto.CreateReminderRequest {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.util.List,java.lang.Integer,java.lang.String,int,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
}

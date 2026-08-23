-if class com.dopashift.data.remote.dto.UpdateTodoRequest
-keepnames class com.dopashift.data.remote.dto.UpdateTodoRequest
-if class com.dopashift.data.remote.dto.UpdateTodoRequest
-keep class com.dopashift.data.remote.dto.UpdateTodoRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.dopashift.data.remote.dto.UpdateTodoRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.dopashift.data.remote.dto.UpdateTodoRequest
-keepclassmembers class com.dopashift.data.remote.dto.UpdateTodoRequest {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}

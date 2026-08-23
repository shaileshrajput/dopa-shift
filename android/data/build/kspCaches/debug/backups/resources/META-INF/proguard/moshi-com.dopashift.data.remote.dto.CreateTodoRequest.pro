-if class com.dopashift.data.remote.dto.CreateTodoRequest
-keepnames class com.dopashift.data.remote.dto.CreateTodoRequest
-if class com.dopashift.data.remote.dto.CreateTodoRequest
-keep class com.dopashift.data.remote.dto.CreateTodoRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.dopashift.data.remote.dto.CreateTodoRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.dopashift.data.remote.dto.CreateTodoRequest
-keepclassmembers class com.dopashift.data.remote.dto.CreateTodoRequest {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}

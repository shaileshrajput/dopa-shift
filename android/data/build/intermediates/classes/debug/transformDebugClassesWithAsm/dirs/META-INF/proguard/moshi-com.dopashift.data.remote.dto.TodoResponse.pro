-if class com.dopashift.data.remote.dto.TodoResponse
-keepnames class com.dopashift.data.remote.dto.TodoResponse
-if class com.dopashift.data.remote.dto.TodoResponse
-keep class com.dopashift.data.remote.dto.TodoResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

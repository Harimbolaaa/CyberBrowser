# Conserver l'interface JavaScript du pont WebView (window.AndroidCyberBridge)
-keepattributes JavascriptInterface
-keepclassmembers class com.example.CyberJsBridge {
    @android.webkit.JavascriptInterface <methods>;
    public *;
}

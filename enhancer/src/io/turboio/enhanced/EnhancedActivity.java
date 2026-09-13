package io.turboio.enhanced;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Safe demo launcher. Pairing and every official setting remain in the official Activity. */
public final class EnhancedActivity extends Activity {
    private DemoController controller;
    private EditText key,paseoUrl,paseoPassword;
    private TextView status,transcript,saved,paseoSaved,diagnostics;
    private Button toggle,save,clear,savePaseo,testPaseo,clearPaseoPassword;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy,visible;
    private final Runnable refresh=new Runnable(){public void run(){if(!visible)return;render();main.postDelayed(this,400);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        DemoTrace.init(this);controller=DemoController.get(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(20*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);scroll.addView(root);setContentView(scroll);
        TextView title=label(root,"Turbo IO · Paseo 眼镜桥接");title.setTextSize(23);
        label(root,"眼镜语音 → MiniMax ASR → 带上下文的 MiniMax 路由。普通问答由 MiniMax 流式回答并可自动联网搜索；只有代码任务和会话命令进入 Paseo/Codex。");
        button(root,"打开官方 App",()->{Intent intent=new Intent();intent.setClassName(getPackageName(),"com.rayneo.venus.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);});
        label(root,"MiniMax API Key");
        key=new EditText(this);key.setHint("在此粘贴 API Key");key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);root.addView(key);
        saved=label(root,"");
        save=button(root,"保存到本机",()->{final String value=key.getText().toString();key.setText("");run(()->{if(!controller.canEditKey())throw new IllegalStateException("请先关闭模式");controller.keys.save(value);});});
        clear=button(root,"删除本机 Key",()->run(()->{if(!controller.canEditKey())throw new IllegalStateException("请先关闭模式");controller.keys.clear();}));
        label(root,"此 Key 固定用于 MiniMax ASR、意图识别和 Responses 流式回答，经系统密钥库加密保存。当前版本暂不开放其他模型配置。");
        label(root,"Paseo WebSocket URL");
        paseoUrl=new EditText(this);paseoUrl.setHint("ws://电脑局域网IP:6767/ws");paseoUrl.setSingleLine(true);paseoUrl.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);paseoUrl.setText(controller.paseoUrl());root.addView(paseoUrl);
        label(root,"Paseo daemon 密码（留空保留现有值）");
        paseoPassword=new EditText(this);paseoPassword.setHint("首次无密码可留空；不会回显或写日志");paseoPassword.setSingleLine(true);paseoPassword.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);paseoPassword.setSaveEnabled(false);paseoPassword.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);root.addView(paseoPassword);
        paseoSaved=label(root,"");
        savePaseo=button(root,"保存 Paseo 配置",()->{final String url=paseoUrl.getText().toString(),password=paseoPassword.getText().toString();paseoPassword.setText("");run(()->controller.savePaseo(url,password));});
        clearPaseoPassword=button(root,"删除本机 Paseo 密码",()->run(()->controller.clearPaseoPassword()));
        testPaseo=button(root,"测试 Paseo 连接",()->run(()->controller.testPaseo()));
        label(root,"手机必须能访问该地址；电脑上的 127.0.0.1 不能从手机直接访问。密码使用 AndroidKeyStore 加密保存。");
        toggle=button(root,"开启 Paseo 眼镜模式",()->run(()->{if(controller.enabled())controller.disable();else controller.enable();}));
        button(root,"结束当前这句话",()->run(()->controller.finishUtterance()));
        button(root,"查看诊断记录（重启后保留）",()->{
            TextView text=new TextView(this);text.setText(DemoTrace.recent());text.setTextIsSelectable(true);text.setPadding(24,24,24,24);
            ScrollView history=new ScrollView(this);history.addView(text);
            new android.app.AlertDialog.Builder(this).setTitle("诊断记录 · 不含 Key 和语音正文").setView(history).setPositiveButton("关闭",null).show();
        });
        status=label(root,"");diagnostics=label(root,"");transcript=label(root,"");transcript.setTextIsSelectable(true);
    }
    private interface Work{void run()throws Exception;}
    private void run(Work task){if(busy)return;busy=true;render();worker.execute(()->{String error=null;try{task.run();}catch(Exception|LinkageError e){error=e instanceof IllegalStateException||e instanceof IllegalArgumentException?e.getMessage():"操作失败："+e.getClass().getSimpleName();}final String message=error;main.post(()->{busy=false;if(isDestroyed())return;if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();render();});});}
    private TextView label(LinearLayout root,String value){TextView t=new TextView(this);t.setText(value);t.setTextSize(16);t.setPadding(0,12,0,12);root.addView(t);return t;}
    private Button button(LinearLayout root,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());root.addView(b);return b;}
    private static void textIfChanged(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private static void enabledIfChanged(View view,boolean enabled){if(view.isEnabled()!=enabled)view.setEnabled(enabled);}
    private void render(){
        if(status==null)return;
        boolean editable=controller.canEditKey()&&!busy;
        enabledIfChanged(key,editable);enabledIfChanged(save,editable);enabledIfChanged(clear,editable);enabledIfChanged(toggle,!busy);
        enabledIfChanged(paseoUrl,editable);enabledIfChanged(paseoPassword,editable);enabledIfChanged(savePaseo,editable);enabledIfChanged(clearPaseoPassword,editable);enabledIfChanged(testPaseo,editable);
        textIfChanged(toggle,controller.enabled()?"关闭 Paseo 眼镜模式 / 重试清理":"开启 Paseo 眼镜模式");
        textIfChanged(status,controller.status());textIfChanged(diagnostics,controller.diagnostics());
        textIfChanged(saved,controller.keys.hasKey()?"已保存 Key（不回显）":"尚未保存 Key");
        textIfChanged(paseoSaved,controller.paseoUrl().isEmpty()?"尚未保存 Paseo 地址":"已保存："+controller.paseoUrl()+(controller.paseoConfig.hasPassword()?"（含密码）":"（无密码）"));
        textIfChanged(transcript,"最近识别：\n"+controller.transcript());
    }
    @Override protected void onResume(){super.onResume();visible=true;main.post(refresh);worker.execute(()->DemoTrace.record("state "+VendorTransport.diagnostics()));}
    @Override protected void onPause(){visible=false;main.removeCallbacks(refresh);key.setText("");paseoPassword.setText("");super.onPause();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}

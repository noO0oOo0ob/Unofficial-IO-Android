package io.turboio.enhanced;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, code-only TurboIO console layered onto the preserved official APK. */
public final class EnhancedActivity extends Activity {
    private static final int BG=Color.rgb(5,6,6),SURFACE=Color.rgb(28,30,30),SURFACE_2=Color.rgb(40,43,42);
    private static final int LINE=Color.rgb(52,55,54),TEXT=Color.rgb(242,244,243),MUTED=Color.rgb(139,144,142);
    private static final int ACCENT=Color.rgb(77,181,150),ACCENT_SOFT=Color.rgb(36,75,64),DANGER=Color.rgb(229,114,114);

    private enum Page { HOME, DEBUG, LOGS, SETTINGS, AGENT_PLATFORMS, PASEO_SOURCES, PASEO_SOURCE_EDIT, MANAGED_AGENT_CONFIG }
    private interface Work { String run()throws Exception; }
    private interface Done { void accept(String value); }

    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<Page,View> pages=new EnumMap<>(Page.class);
    private final Map<Page,TextView> navItems=new EnumMap<>(Page.class);
    private final List<View> sourceActions=new ArrayList<>();
    private DemoController controller;
    private FrameLayout pageHost;
    private Page currentPage=Page.HOME;
    private boolean busy,visible,logsPaused;
    private String deviceState="检查中",logFilter="ALL";

    private TextView modeState,modeStatus,modeButton,deviceSummary,agentSummary,runtimeSummary,recentTranscript,finishButton;
    private EditText key,sourceName,sourceUrl,sourcePassword,maBaseUrl,maApiKey,maEnvironmentId,maAgentId,maTitle,maVersion,assistantText,businessId,rawHex,logSearch;
    private TextView keySaved,platformSaved,maKeySaved,amapStatus,debugResult,logText,logBadge,pauseLogs;
    private TextView sendAssistant,sendRaw,saveKey,clearKey,testPaseo,managePlatform,savePaseoSource,deletePaseoSource,saveManagedAgent,clearManagedAgentKey,notificationAccess;
    private Switch amapNavigation;
    private ScrollView logsScroll;
    private String editingSourceId;

    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(!visible)return;render();main.postDelayed(this,400);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=28)getWindow().setNavigationBarDividerColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        DemoTrace.init(this);controller=DemoController.get(this);
        setContentView(buildShell());showPage(Page.HOME);
    }

    private View buildShell() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        pageHost=new FrameLayout(this);root.addView(pageHost,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(12),dp(8),dp(12),dp(8));nav.setBackgroundColor(Color.rgb(17,19,19));
        addNav(nav,Page.HOME,"⌂\n首页");addNav(nav,Page.DEBUG,"⌁\n调试");addNav(nav,Page.LOGS,"≡\n日志");addNav(nav,Page.SETTINGS,"⚙\n设置");
        root.addView(nav,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(76)));
        return root;
    }

    private void addNav(LinearLayout nav,Page page,String value) {
        TextView item=text(value,11,TEXT,Typeface.NORMAL);item.setGravity(Gravity.CENTER);item.setLineSpacing(dp(2),1f);
        item.setOnClickListener(v->showPage(page));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.setMargins(dp(5),0,dp(5),0);
        nav.addView(item,p);navItems.put(page,item);
    }

    private void showPage(Page page) {
        currentPage=page;if(page==Page.SETTINGS||page==Page.AGENT_PLATFORMS||page==Page.PASEO_SOURCES||page==Page.PASEO_SOURCE_EDIT||page==Page.MANAGED_AGENT_CONFIG)pages.remove(page);View content=pages.get(page);
        if(content==null){content=createPage(page);pages.put(page,content);}
        pageHost.removeAllViews();pageHost.addView(content,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        for(Map.Entry<Page,TextView> entry:navItems.entrySet()){
            boolean active=entry.getKey()==page||(entry.getKey()==Page.SETTINGS&&(page==Page.AGENT_PLATFORMS||page==Page.PASEO_SOURCES||page==Page.PASEO_SOURCE_EDIT||page==Page.MANAGED_AGENT_CONFIG));entry.getValue().setTextColor(active?ACCENT:MUTED);
            entry.getValue().setBackground(round(active?ACCENT_SOFT:Color.TRANSPARENT,18));
        }
        render();
    }

    private View createPage(Page page) {
        switch(page){case DEBUG:return buildDebug();case LOGS:return buildLogs();case SETTINGS:return buildSettings();case AGENT_PLATFORMS:return buildAgentPlatforms();case PASEO_SOURCES:return buildPaseoSources();case PASEO_SOURCE_EDIT:return buildPaseoSourceEdit();case MANAGED_AGENT_CONFIG:return buildManagedAgentConfig();default:return buildHome();}
    }

    private ScrollView page(String title,String subtitle,LinearLayout[] output) {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout body=column();body.setPadding(dp(18),dp(18),dp(18),dp(24));
        body.addView(text(title,28,TEXT,Typeface.BOLD));TextView sub=text(subtitle,12,MUTED,Typeface.NORMAL);body.addView(sub,matchWrap(dp(2),0,0,dp(14)));
        scroll.addView(body);output[0]=body;return scroll;
    }

    private View buildHome() {
        LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page("TurboIO","RayNeo 智能眼镜控制台",holder);LinearLayout body=holder[0];
        LinearLayout mode=card();LinearLayout title=row();TextView name=text("✦  TurboIO 接管模式",16,TEXT,Typeface.BOLD);title.addView(name,new LinearLayout.LayoutParams(0,dp(32),1));
        modeState=badge("已关闭",MUTED,SURFACE_2);title.addView(modeState);mode.addView(title);
        modeStatus=text("关闭时，眼镜普通语音仍由官方助手处理。",12,MUTED,Typeface.NORMAL);mode.addView(modeStatus,matchWrap(0,dp(7),0,dp(12)));
        modeButton=button("开启接管模式",true);modeButton.setOnClickListener(v->run(()->{if(controller.enabled())controller.disable();else controller.enable();return null;},null));mode.addView(modeButton);
        body.addView(mode,matchWrap(0,0,0,dp(12)));

        LinearLayout summaries=row();deviceSummary=summary(summaries,"眼镜","检查中");agentSummary=summary(summaries,"Agent 平台","未连接");runtimeSummary=summary(summaries,"语音运行时","待机");body.addView(summaries,matchWrap(0,0,0,dp(12)));
        LinearLayout recent=card();LinearLayout recentTitle=row();recentTitle.addView(text("最近识别",12,MUTED,Typeface.NORMAL),new LinearLayout.LayoutParams(0,dp(28),1));recentTitle.addView(badge("等待语音",ACCENT,ACCENT_SOFT));recent.addView(recentTitle);
        recentTranscript=text("尚无识别内容",14,TEXT,Typeface.NORMAL);recentTranscript.setMinHeight(dp(42));recent.addView(recentTranscript,matchWrap(0,dp(5),0,0));body.addView(recent,matchWrap(0,0,0,dp(12)));

        LinearLayout actions=row();TextView official=button("↗  打开官方 App",false);official.setOnClickListener(v->openOfficial());actions.addView(official,weighted(1,0,dp(5),0));
        finishButton=button("■  结束当前这句话",false);finishButton.setOnClickListener(v->run(()->{controller.finishUtterance();return null;},null));actions.addView(finishButton,weighted(1,dp(5),0,0));body.addView(actions);
        return scroll;
    }

    private View buildDebug() {
        LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page("消息调试","仅向当前唯一连接的眼镜发送测试消息",holder);LinearLayout body=holder[0];
        LinearLayout connection=card();LinearLayout connectionRow=row();connectionRow.addView(text("⌁  当前目标",12,MUTED,Typeface.NORMAL),new LinearLayout.LayoutParams(0,dp(30),1));connectionRow.addView(badge("自动识别",ACCENT,ACCENT_SOFT));connection.addView(connectionRow);
        connection.addView(text("由官方 App 管理的唯一连接设备",14,TEXT,Typeface.BOLD));body.addView(connection,matchWrap(0,0,0,dp(12)));

        LinearLayout assistant=card();assistant.addView(sectionTitle("♩  Assistant / ASR","Business 13"));assistantText=input("输入要显示到眼镜助手页的文字",false);assistantText.setMinHeight(dp(82));assistantText.setGravity(Gravity.TOP);assistantText.setSingleLine(false);assistant.addView(assistantText,matchWrap(0,dp(10),0,dp(10)));
        sendAssistant=button("➤  提交到发送链路",true);sendAssistant.setOnClickListener(v->{String value=assistantText.getText().toString();run(()->DebugMessageSender.sendAssistant(controller,value),result->{debugResult.setText(result);assistantText.setText("");});});assistant.addView(sendAssistant);body.addView(assistant,matchWrap(0,0,0,dp(12)));

        LinearLayout raw=card();raw.addView(sectionTitle("{ }  原始 Business 消息","高级调试"));LinearLayout fields=row();businessId=input("Business ID",true);businessId.setText("13");fields.addView(businessId,weighted(1,0,dp(5),0));rawHex=input("Hex payload，例如 08011005",false);fields.addView(rawHex,weighted(2,dp(5),0,0));raw.addView(fields,matchWrap(0,dp(10),0,dp(10)));
        sendRaw=button("➤  发送原始消息",false);sendRaw.setOnClickListener(v->{String business=businessId.getText().toString(),payload=rawHex.getText().toString();run(()->DebugMessageSender.sendRaw(controller,business,payload),result->{debugResult.setText(result);rawHex.setText("");});});raw.addView(sendRaw);body.addView(raw,matchWrap(0,0,0,dp(12)));
        debugResult=text("发送结果会显示在这里。成功回调不代表镜片已经显示。",12,MUTED,Typeface.NORMAL);debugResult.setBackground(round(Color.rgb(24,35,31),11));debugResult.setPadding(dp(12),dp(11),dp(12),dp(11));body.addView(debugResult);
        return scroll;
    }

    private View buildLogs() {
        LinearLayout[] holder=new LinearLayout[1];logsScroll=page("诊断日志","本机持久化 · 不记录 Key、设备标识和语音正文",holder);LinearLayout body=holder[0];
        LinearLayout state=row();state.addView(text("运行事件",14,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,dp(32),1));logBadge=badge("记录中",ACCENT,ACCENT_SOFT);state.addView(logBadge);body.addView(state,matchWrap(0,0,0,dp(8)));
        logSearch=input("搜索事件或错误",false);logSearch.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderLogs();}public void afterTextChanged(Editable e){}});body.addView(logSearch,matchWrap(0,0,0,dp(9)));
        LinearLayout filters=row();addFilter(filters,"全部","ALL");addFilter(filters,"状态","STATE");addFilter(filters,"发送","SEND");addFilter(filters,"错误","ERROR");body.addView(filters,matchWrap(0,0,0,dp(9)));
        logText=text("暂无记录",11,TEXT,Typeface.NORMAL);logText.setTypeface(Typeface.MONOSPACE);logText.setTextIsSelectable(true);logText.setGravity(Gravity.TOP);logText.setPadding(dp(12),dp(12),dp(12),dp(12));logText.setBackground(border(Color.rgb(17,19,19),LINE,13));body.addView(logText,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(430)));
        LinearLayout actions=row();pauseLogs=smallAction("Ⅱ  暂停");pauseLogs.setOnClickListener(v->{logsPaused=!logsPaused;renderLogs();});actions.addView(pauseLogs,weighted(1,0,dp(4),0));
        TextView bottom=smallAction("↓  到底部");bottom.setOnClickListener(v->logsScroll.fullScroll(View.FOCUS_DOWN));actions.addView(bottom,weighted(1,dp(4),dp(4),0));
        TextView copy=smallAction("□  复制");copy.setOnClickListener(v->copyLogs());actions.addView(copy,weighted(1,dp(4),dp(4),0));
        TextView clear=smallAction("×  清空");clear.setTextColor(DANGER);clear.setOnClickListener(v->confirmClearLogs());actions.addView(clear,weighted(1,dp(4),0,0));body.addView(actions,matchWrap(0,dp(9),0,0));
        return logsScroll;
    }

    private View buildSettings() {
        sourceActions.clear();sourceName=sourceUrl=sourcePassword=null;savePaseoSource=deletePaseoSource=null;maBaseUrl=maApiKey=maEnvironmentId=maAgentId=maTitle=maVersion=null;LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page("设置","只保留当前版本真正生效的配置",holder);LinearLayout body=holder[0];
        LinearLayout keyCard=card();keyCard.addView(sectionTitle("⌑  MiniMax API Key","ASR · 路由 · 问答"));key=input("留空不会覆盖现有 Key",false);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);keyCard.addView(key,matchWrap(0,dp(9),0,dp(8)));
        keySaved=text("",11,MUTED,Typeface.NORMAL);keyCard.addView(keySaved,matchWrap(0,0,0,dp(8)));LinearLayout keyActions=row();saveKey=button("保存到本机",true);saveKey.setOnClickListener(v->{String value=key.getText().toString();key.setText("");run(()->{controller.keys.save(value);return "MiniMax Key 已保存";},this::toast);});keyActions.addView(saveKey,weighted(1,0,dp(5),0));clearKey=button("删除 Key",false);clearKey.setTextColor(DANGER);clearKey.setOnClickListener(v->run(()->{controller.keys.clear();return "MiniMax Key 已删除";},this::toast));keyActions.addView(clearKey,weighted(1,dp(5),0,0));keyCard.addView(keyActions);body.addView(keyCard,matchWrap(0,0,0,dp(12)));

        LinearLayout platformCard=card();platformCard.addView(sectionTitle("⌁  Agent 平台","启动时装配"));platformSaved=text("",12,TEXT,Typeface.BOLD);platformCard.addView(platformSaved,matchWrap(0,dp(10),0,dp(10)));managePlatform=button("选择并配置 Agent 平台",true);managePlatform.setOnClickListener(v->showPage(Page.AGENT_PLATFORMS));platformCard.addView(managePlatform);body.addView(platformCard,matchWrap(0,0,0,dp(12)));

        LinearLayout amapCard=card();LinearLayout amapRow=row();LinearLayout amapText=column();amapText.addView(text("▲  高德导航提示",14,TEXT,Typeface.BOLD));amapText.addView(text("转发标准导航通知，需要系统通知使用权",10,MUTED,Typeface.NORMAL));amapRow.addView(amapText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));amapNavigation=new Switch(this);amapNavigation.setChecked(AmapNavigationListenerService.isEnabled(this));amapNavigation.setOnCheckedChangeListener((button,checked)->{AmapNavigationListenerService.setEnabled(this,checked);if(checked&&!AmapNavigationListenerService.hasAccess(this))openNotificationAccess();render();});amapRow.addView(amapNavigation);amapCard.addView(amapRow);
        amapStatus=text("",11,MUTED,Typeface.NORMAL);amapCard.addView(amapStatus,matchWrap(0,dp(7),0,dp(8)));notificationAccess=button("打开系统通知使用权设置",false);notificationAccess.setOnClickListener(v->openNotificationAccess());amapCard.addView(notificationAccess);body.addView(amapCard);
        return scroll;
    }

    private View buildAgentPlatforms() {
        sourceActions.clear();LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page("Agent 平台","平台决定协议、会话语义和配置表单",holder);LinearLayout body=holder[0];
        TextView warning=text("切换平台前需要关闭 TurboIO 接管模式。Paseo 与 Managed Agent 的会话选择彼此独立。",11,ACCENT,Typeface.NORMAL);warning.setPadding(dp(12),dp(10),dp(12),dp(10));warning.setBackground(round(ACCENT_SOFT,11));body.addView(warning,matchWrap(0,0,0,dp(12)));
        addPlatformCard(body,AgentPlatform.PASEO,"本地 Paseo daemon","WebSocket · 多电脑源 · 工作区创建",Page.PASEO_SOURCES);
        addPlatformCard(body,AgentPlatform.MANAGED_AGENT,"CatPaw Managed Agent","REST + SSE · Environment · Agent 模板",Page.MANAGED_AGENT_CONFIG);
        return scroll;
    }

    private void addPlatformCard(LinearLayout body,AgentPlatform platform,String title,String detail,Page configPage) {
        boolean current=controller.platform()==platform;LinearLayout platformCard=card();if(current)platformCard.setBackground(border(Color.rgb(20,61,50),ACCENT,14));
        LinearLayout header=row();header.addView(text((platform==AgentPlatform.PASEO?"▣  ":"✦  ")+title,15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,dp(30),1));if(current)header.addView(badge("当前使用",ACCENT,ACCENT_SOFT));platformCard.addView(header);platformCard.addView(text(detail,11,MUTED,Typeface.NORMAL),matchWrap(0,dp(3),0,dp(10)));
        LinearLayout actions=row();TextView configure=button("打开配置",false);configure.setOnClickListener(v->showPage(configPage));actions.addView(configure,weighted(1,0,dp(5),0));TextView select=button(current?"已选择":"切换到此平台",!current);select.setOnClickListener(v->{if(!current)run(()->{controller.selectPlatform(platform);return "已切换到 "+platform.label;},value->{toast(value);showPage(Page.AGENT_PLATFORMS);});});actions.addView(select,weighted(1,dp(5),0,0));platformCard.addView(actions);sourceActions.add(configure);sourceActions.add(select);body.addView(platformCard,matchWrap(0,0,0,dp(10)));
    }

    private View buildManagedAgentConfig() {
        sourceActions.clear();LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page("Managed Agent 平台配置","网页负责模板能力，端上负责 Session 和指定 Agent",holder);LinearLayout body=holder[0];
        TextView back=smallAction("‹  返回 Agent 平台");back.setOnClickListener(v->showPage(Page.AGENT_PLATFORMS));sourceActions.add(back);body.addView(back,matchWrap(0,0,0,dp(12)));
        LinearLayout form=card();form.addView(text("Base URL",11,MUTED,Typeface.NORMAL));maBaseUrl=input("https://你的-managed-agent-服务",false);maBaseUrl.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);maBaseUrl.setText(controller.platformConfig.baseUrl());form.addView(maBaseUrl,matchWrap(0,dp(6),0,dp(10)));
        form.addView(text("API Key",11,MUTED,Typeface.NORMAL));maApiKey=input(controller.platformConfig.hasApiKey()?"留空则保留已保存的 Key":"输入 Managed Agent API Key",false);maApiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);maApiKey.setSaveEnabled(false);maApiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);form.addView(maApiKey,matchWrap(0,dp(6),0,dp(5)));maKeySaved=text(controller.platformConfig.hasApiKey()?"● API Key 已加密保存":"○ 尚未保存 API Key",10,controller.platformConfig.hasApiKey()?ACCENT:MUTED,Typeface.NORMAL);form.addView(maKeySaved,matchWrap(0,0,0,dp(10)));
        form.addView(text("Environment ID",11,MUTED,Typeface.NORMAL));maEnvironmentId=input("env_xxx",false);maEnvironmentId.setText(controller.platformConfig.environmentId());form.addView(maEnvironmentId,matchWrap(0,dp(6),0,dp(10)));
        form.addView(text("Agent ID",11,MUTED,Typeface.NORMAL));maAgentId=input("agent_xxx",false);maAgentId.setText(controller.platformConfig.agentId());form.addView(maAgentId,matchWrap(0,dp(6),0,dp(10)));
        form.addView(text("Agent Version（可选）",11,MUTED,Typeface.NORMAL));maVersion=input("留空使用网页发布的最新版本",true);maVersion.setText(controller.platformConfig.version());form.addView(maVersion,matchWrap(0,dp(6),0,dp(10)));
        form.addView(text("新会话标题（可选）",11,MUTED,Typeface.NORMAL));maTitle=input("RayNeo 眼镜会话",false);maTitle.setText(controller.platformConfig.title());form.addView(maTitle,matchWrap(0,dp(6),0,dp(7)));form.addView(text("Environment 和 Agent 模板在 Managed Agent 网页配置；端上只保存引用并管理 Session。",10,MUTED,Typeface.NORMAL));body.addView(form,matchWrap(0,0,0,dp(10)));
        saveManagedAgent=button("保存并测试连接",true);saveManagedAgent.setOnClickListener(v->{String base=maBaseUrl.getText().toString(),apiKey=maApiKey.getText().toString(),env=maEnvironmentId.getText().toString(),agent=maAgentId.getText().toString(),title=maTitle.getText().toString(),version=maVersion.getText().toString();maApiKey.setText("");run(()->{controller.saveManagedAgent(base,env,agent,title,version,apiKey);controller.testManagedAgent();return "Managed Agent 配置已保存并连接成功";},this::toast);});sourceActions.add(saveManagedAgent);body.addView(saveManagedAgent);
        if(controller.platformConfig.hasApiKey()){clearManagedAgentKey=smallAction("删除已保存的 API Key");clearManagedAgentKey.setTextColor(DANGER);clearManagedAgentKey.setOnClickListener(v->run(()->{controller.clearManagedAgentKey();return "Managed Agent API Key 已删除";},value->{toast(value);showPage(Page.MANAGED_AGENT_CONFIG);}));sourceActions.add(clearManagedAgentKey);body.addView(clearManagedAgentKey,matchWrap(0,dp(9),0,0));}
        return scroll;
    }

    private View buildPaseoSources() {
        sourceActions.clear();LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page("Paseo 源","点击切换公司、家庭或其他电脑",holder);LinearLayout body=holder[0];
        TextView add=button("＋  添加 Paseo 源",true);add.setOnClickListener(v->{editingSourceId=null;showPage(Page.PASEO_SOURCE_EDIT);});sourceActions.add(add);body.addView(add,matchWrap(0,0,0,dp(10)));
        TextView warning=text("切换前需要关闭 TurboIO 接管模式，避免当前语音轮次跨到另一台电脑。",11,ACCENT,Typeface.NORMAL);warning.setPadding(dp(12),dp(10),dp(12),dp(10));warning.setBackground(round(ACCENT_SOFT,11));body.addView(warning,matchWrap(0,0,0,dp(12)));
        List<PaseoConfigStore.Source> sources=controller.paseoSources();String active=controller.paseoConfig.activeSourceId();
        if(sources.isEmpty()){LinearLayout empty=card();empty.addView(text("还没有 Paseo 源",15,TEXT,Typeface.BOLD));empty.addView(text("添加你当前网络下可访问的电脑地址。",11,MUTED,Typeface.NORMAL),matchWrap(0,dp(7),0,0));body.addView(empty);}
        for(PaseoConfigStore.Source source:sources){boolean current=source.id.equals(active);LinearLayout sourceCard=card();if(current)sourceCard.setBackground(border(Color.rgb(20,61,50),ACCENT,14));LinearLayout title=row();title.addView(text("▣  "+source.name,15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,dp(30),1));if(current)title.addView(badge("当前使用",ACCENT,ACCENT_SOFT));sourceCard.addView(title);sourceCard.addView(text(source.url,11,MUTED,Typeface.NORMAL),matchWrap(0,dp(4),0,dp(7)));sourceCard.addView(text(source.hasPassword?"● 已保存密码":"○ 未设置密码",10,source.hasPassword?ACCENT:MUTED,Typeface.NORMAL));
            LinearLayout actions=row();TextView test=smallAction("测试连接");test.setOnClickListener(v->run(()->{controller.testPaseoSource(source.id);return source.name+" 连接成功";},this::toast));actions.addView(test,weighted(1,0,dp(4),0));TextView select=button(current?"当前使用":"切换到此源",!current);select.setOnClickListener(v->{if(!current)run(()->{controller.switchPaseoSource(source.id);return "已切换到 "+source.name;},value->{toast(value);showPage(Page.PASEO_SOURCES);});});actions.addView(select,weighted(1,dp(4),dp(4),0));TextView edit=smallAction("编辑");edit.setOnClickListener(v->{editingSourceId=source.id;showPage(Page.PASEO_SOURCE_EDIT);});actions.addView(edit,weighted(.7f,dp(4),0,0));sourceCard.addView(actions,matchWrap(0,dp(10),0,0));sourceActions.add(test);sourceActions.add(select);sourceActions.add(edit);body.addView(sourceCard,matchWrap(0,0,0,dp(10)));
        }
        return scroll;
    }

    private View buildPaseoSourceEdit() {
        sourceActions.clear();PaseoConfigStore.Source editing=null;for(PaseoConfigStore.Source source:controller.paseoSources())if(source.id.equals(editingSourceId)){editing=source;break;}final PaseoConfigStore.Source existing=editing;
        LinearLayout[] holder=new LinearLayout[1];ScrollView scroll=page(existing==null?"新建 Paseo 源":"编辑 Paseo 源",existing==null?"连接一台新的 daemon":"修改 "+existing.name,holder);LinearLayout body=holder[0];
        TextView back=smallAction("‹  返回 Paseo 源");back.setOnClickListener(v->showPage(Page.PASEO_SOURCES));sourceActions.add(back);body.addView(back,matchWrap(0,0,0,dp(12)));
        LinearLayout form=card();form.addView(text("名称",11,MUTED,Typeface.NORMAL));sourceName=input("例如：公司电脑",false);sourceName.setText(existing==null?"":existing.name);form.addView(sourceName,matchWrap(0,dp(6),0,dp(10)));form.addView(text("WebSocket URL",11,MUTED,Typeface.NORMAL));sourceUrl=input("ws://电脑局域网IP:6767/ws",false);sourceUrl.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);sourceUrl.setText(existing==null?"":existing.url);form.addView(sourceUrl,matchWrap(0,dp(6),0,dp(10)));form.addView(text("访问密码",11,MUTED,Typeface.NORMAL));sourcePassword=input(existing!=null&&existing.hasPassword?"留空则保留当前密码":"daemon 未启用密码时可留空",false);sourcePassword.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);sourcePassword.setSaveEnabled(false);sourcePassword.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);form.addView(sourcePassword,matchWrap(0,dp(6),0,dp(7)));form.addView(text("每个源的密码、Client ID、最近会话和工作区互相独立。",10,MUTED,Typeface.NORMAL));body.addView(form,matchWrap(0,0,0,dp(10)));
        savePaseoSource=button("保存并测试连接",true);savePaseoSource.setOnClickListener(v->{String name=sourceName.getText().toString(),url=sourceUrl.getText().toString(),password=sourcePassword.getText().toString(),id=existing==null?null:existing.id;sourcePassword.setText("");run(()->{String saved=controller.savePaseoSource(id,name,url,password);controller.testPaseoSource(saved);return "Paseo 源已保存并连接成功";},value->{toast(value);showPage(Page.PASEO_SOURCES);});});sourceActions.add(savePaseoSource);body.addView(savePaseoSource);
        if(existing!=null){if(existing.hasPassword){TextView clearPassword=smallAction("删除已保存密码");clearPassword.setOnClickListener(v->run(()->{controller.clearPaseoSourcePassword(existing.id);return "此源的密码已删除";},value->{toast(value);showPage(Page.PASEO_SOURCE_EDIT);}));sourceActions.add(clearPassword);body.addView(clearPassword,matchWrap(0,dp(9),0,0));}deletePaseoSource=smallAction("删除此源");deletePaseoSource.setTextColor(DANGER);deletePaseoSource.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除“"+existing.name+"”？").setMessage("只删除手机本机保存的源配置和密码，不会影响电脑上的 Paseo daemon。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->run(()->{controller.deletePaseoSource(existing.id);return "Paseo 源已删除";},value->{toast(value);showPage(Page.PASEO_SOURCES);})).show());sourceActions.add(deletePaseoSource);body.addView(deletePaseoSource,matchWrap(0,dp(9),0,0));}
        return scroll;
    }

    private void render() {
        boolean editable=controller.canEditKey()&&!busy,debuggable=controller.canDebugSend()&&!busy;
        if(modeButton!=null){boolean enabled=controller.enabled();modeState.setText(enabled?"接管中":"已关闭");modeState.setTextColor(enabled?ACCENT:MUTED);modeState.setBackground(round(enabled?ACCENT_SOFT:SURFACE_2,14));modeButton.setText(enabled?"关闭接管模式 / 重试清理":"开启接管模式");modeStatus.setText(controller.status());modeButton.setEnabled(!busy);styleEnabled(modeButton,!busy,true);finishButton.setEnabled(enabled&&!busy);styleEnabled(finishButton,enabled&&!busy,false);deviceSummary.setText(deviceState);agentSummary.setText(controller.platformLabel()+" · "+controller.agentState());runtimeSummary.setText(enabled?"接管中":"待机");recentTranscript.setText(controller.transcript().isEmpty()?"尚无识别内容":controller.transcript());}
        if(key!=null){setEnabled(editable,key,saveKey,clearKey,managePlatform);keySaved.setText(controller.keys.hasKey()?"已保存 Key（不会回显）":"尚未保存 Key");platformSaved.setText("当前使用："+controller.platformLabel());boolean nav=AmapNavigationListenerService.isEnabled(this),access=AmapNavigationListenerService.hasAccess(this);if(amapNavigation.isChecked()!=nav)amapNavigation.setChecked(nav);amapStatus.setText(!nav?"状态：已关闭":access?"状态：已开启，等待高德导航通知":"状态：已开启，但尚未授予通知使用权");}
        for(View action:sourceActions){action.setEnabled(editable);action.setAlpha(editable?1f:.42f);}if(sourceName!=null)setEnabled(editable,sourceName,sourceUrl,sourcePassword,savePaseoSource,deletePaseoSource);
        if(maBaseUrl!=null)setEnabled(editable,maBaseUrl,maApiKey,maEnvironmentId,maAgentId,maTitle,maVersion,saveManagedAgent,clearManagedAgentKey);
        if(sendAssistant!=null){setEnabled(debuggable,assistantText,businessId,rawHex,sendAssistant,sendRaw);if(!debuggable&&!busy)debugResult.setText("请先关闭 TurboIO 接管模式并等待当前任务结束。\n发送成功回调不代表镜片已经显示。");}
        if(currentPage==Page.LOGS&&!logsPaused)renderLogs();
    }

    private void renderLogs() {
        if(logText==null)return;logBadge.setText(logsPaused?"已暂停":"记录中");logBadge.setTextColor(logsPaused?MUTED:ACCENT);logBadge.setBackground(round(logsPaused?SURFACE_2:ACCENT_SOFT,14));pauseLogs.setText(logsPaused?"▶  继续":"Ⅱ  暂停");
        if(logsPaused)return;String query=logSearch==null?"":logSearch.getText().toString().trim().toLowerCase(Locale.ROOT);StringBuilder output=new StringBuilder();String recent=DemoTrace.recent();
        for(String line:recent.split("\n")){String lower=line.toLowerCase(Locale.ROOT);if(!query.isEmpty()&&!lower.contains(query))continue;if(!"ALL".equals(logFilter)&&!logFilter.equals(logKind(lower)))continue;if(output.length()>0)output.append('\n');output.append(line);}
        logText.setText(output.length()==0?"没有匹配的日志":output.toString());
    }

    private String logKind(String lower) {
        if(lower.contains("error")||lower.contains("failed")||lower.contains("失败")||lower.contains("timeout"))return "ERROR";
        if(lower.contains("send")||lower.contains("business="))return "SEND";return "STATE";
    }

    private void addFilter(LinearLayout filters,String label,String value) {
        TextView filter=smallAction(label);filter.setOnClickListener(v->{logFilter=value;for(int i=0;i<filters.getChildCount();i++){TextView item=(TextView)filters.getChildAt(i);boolean active=item==filter;item.setTextColor(active?Color.rgb(7,17,14):MUTED);item.setBackground(round(active?ACCENT:SURFACE,15));}renderLogs();});
        filters.addView(filter,weighted(1,filters.getChildCount()==0?0:dp(4),filters.getChildCount()==3?0:dp(4),0));if("ALL".equals(value)){filter.setTextColor(Color.rgb(7,17,14));filter.setBackground(round(ACCENT,15));}
    }

    private void run(Work work,Done done) {
        if(busy)return;busy=true;render();worker.execute(()->{String result=null,error=null;try{result=work.run();}catch(Exception|LinkageError e){error=e instanceof IllegalStateException||e instanceof IllegalArgumentException?e.getMessage():"操作失败："+e.getClass().getSimpleName();}String value=result,failure=error;main.post(()->{busy=false;if(isDestroyed())return;if(failure!=null){toast(failure);if(debugResult!=null&&currentPage==Page.DEBUG)debugResult.setText(failure);}else if(done!=null)done.accept(value);render();});});
    }

    private void probeRuntime() {worker.execute(()->{String value;try{new VendorTransport(false);value="已连接";}catch(Exception|LinkageError e){value="未就绪";}String result=value;main.post(()->{deviceState=result;render();});});}
    private void openOfficial(){try{Intent intent=new Intent();intent.setClassName(getPackageName(),"com.rayneo.venus.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}catch(Exception e){toast("无法打开官方 App");}}
    private void openNotificationAccess(){try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception e){toast("无法打开通知使用权设置");}}
    private void copyLogs(){ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("TurboIO diagnostics",logText.getText()));toast("已复制当前日志");}}
    private void confirmClearLogs(){new AlertDialog.Builder(this).setTitle("清空诊断日志？").setMessage("只清空当前本机记录，不会改变接管模式或连接状态。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->run(()->{DemoTrace.clear();return "诊断日志已清空";},value->{toast(value);renderLogs();})).show();}
    private void toast(String value){if(value!=null&&!value.isEmpty())Toast.makeText(this,value,Toast.LENGTH_LONG).show();}

    private LinearLayout card(){LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(round(SURFACE,14));return card;}
    private LinearLayout row(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);return row;}
    private LinearLayout column(){LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);return column;}
    private View sectionTitle(String left,String right){LinearLayout row=row();row.addView(text(left,13,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));row.addView(text(right,10,MUTED,Typeface.NORMAL));return row;}
    private TextView summary(LinearLayout parent,String label,String value){LinearLayout box=column();box.setPadding(dp(11),dp(10),dp(11),dp(10));box.setBackground(round(SURFACE,12));box.addView(text(label,10,MUTED,Typeface.NORMAL));TextView result=text(value,12,TEXT,Typeface.BOLD);box.addView(result,matchWrap(0,dp(6),0,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(parent.getChildCount()==0?0:dp(4),0,dp(4),0);parent.addView(box,p);return result;}
    private EditText input(String hint,boolean numeric){EditText input=new EditText(this);input.setHint(hint);input.setHintTextColor(MUTED);input.setTextColor(TEXT);input.setTextSize(12);input.setSingleLine(true);input.setPadding(dp(12),0,dp(12),0);input.setBackground(round(SURFACE_2,10));input.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);input.setMinHeight(dp(42));return input;}
    private TextView button(String label,boolean primary){TextView button=text(label,12,primary?Color.rgb(7,17,14):TEXT,Typeface.BOLD);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(44));button.setPadding(dp(10),0,dp(10),0);button.setBackground(round(primary?ACCENT:SURFACE_2,11));button.setClickable(true);return button;}
    private TextView smallAction(String label){TextView action=button(label,false);action.setTextSize(10);action.setMinHeight(dp(38));return action;}
    private TextView badge(String value,int color,int background){TextView badge=text(value,10,color,Typeface.BOLD);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(9),dp(5),dp(9),dp(5));badge.setBackground(round(background,14));return badge;}
    private TextView text(String value,float size,int color,int style){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setTypeface(Typeface.create("sans-serif",style));return text;}
    private GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable border(int color,int stroke,float radius){GradientDrawable d=round(color,radius);d.setStroke(dp(1),stroke);return d;}
    private LinearLayout.LayoutParams matchWrap(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(l,t,r,b);return p;}
    private LinearLayout.LayoutParams weighted(float weight,int l,int r,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),weight);p.setMargins(l,top,r,0);return p;}
    private void setEnabled(boolean enabled,View... views){for(View view:views)if(view!=null){view.setEnabled(enabled);view.setAlpha(enabled?1f:.42f);}}
    private void styleEnabled(TextView view,boolean enabled,boolean primary){view.setAlpha(enabled?1f:.42f);view.setBackground(round(primary?ACCENT:SURFACE_2,11));}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override protected void onResume(){super.onResume();visible=true;main.post(refresh);probeRuntime();worker.execute(()->DemoTrace.record("state "+VendorTransport.diagnostics()));}
    @Override public void onBackPressed(){if(currentPage==Page.PASEO_SOURCE_EDIT){showPage(Page.PASEO_SOURCES);return;}if(currentPage==Page.PASEO_SOURCES||currentPage==Page.MANAGED_AGENT_CONFIG){showPage(Page.AGENT_PLATFORMS);return;}if(currentPage==Page.AGENT_PLATFORMS){showPage(Page.SETTINGS);return;}super.onBackPressed();}
    @Override protected void onPause(){visible=false;main.removeCallbacks(refresh);if(key!=null)key.setText("");if(sourcePassword!=null)sourcePassword.setText("");if(maApiKey!=null)maApiKey.setText("");super.onPause();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}

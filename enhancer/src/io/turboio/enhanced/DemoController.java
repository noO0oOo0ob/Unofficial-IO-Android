package io.turboio.enhanced;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Owns one glasses assistant page and bridges deterministic voice commands to the selected Agent platform. */
final class DemoController {
    private static final long ASSISTANT_IDLE_TIMEOUT_MS=60000;
    private static DemoController instance;
    static synchronized DemoController get(Context c){if(instance==null)instance=new DemoController(c.getApplicationContext());return instance;}
    final ApiKeyStore keys;final PaseoConfigStore paseoConfig;final AgentPlatformConfigStore platformConfig;
    private final HandlerThread thread=new HandlerThread("TurboIO-audio");private final Handler audio;
    private final ExecutorService network=Executors.newCachedThreadPool();private final VoiceCommandRouter router=new VoiceCommandRouter();
    private VendorTransport transport;private AgentSessionClient agentClient;private boolean agentConnected;private VendorTransport.Decoder decoder;private SpeechBuffer buffer;private MinimaxAsr request;private MinimaxIntentRouter intentRequest;private MinimaxResponsesClient chatRequest;
    private long generation,started,closeTicket;private boolean enabled,recording,ownsDevice,closing,stopping,disableRequested,awaitingStopConfirmation;
    private final List<byte[]> pendingFrames=new ArrayList<>();private byte[] pendingWav;private int networkJobs;
    private Runnable captureTimeout,monitor,asrTimeout;private int wakeCount,audioCount,controlRequests,controlOk,controlFailed;
    private String status="已关闭 · 普通语音由官方处理",transcript="",currentAgentId="",currentAgentTitle="",currentWorkspaceId="",currentWorkspaceTitle="",answerSource="Agent 回复";
    private List<AgentSessionClient.Session> agents=new ArrayList<>();private int listPage,answerPage;private List<String> answerPages=new ArrayList<>();private final Map<String,List<String>> answersByAgent=new HashMap<>();
    private List<AgentSessionClient.CreationTarget> workspaces=new ArrayList<>();private int workspacePage;private boolean awaitingWorkspaceForCreate,nextUtteranceForCodex;
    private final ArrayDeque<String> recentTurns=new ArrayDeque<>();private Runnable streamFlush;private String pendingStreamText="",streamQuery="",streamRoundId="";private long lastStreamAt;private boolean streamSent;
    private boolean duplexListening,duplexSpeechBegan;private long duplexSessionDeadline;

    private DemoController(Context c){keys=new ApiKeyStore(c);paseoConfig=new PaseoConfigStore(c);platformConfig=new AgentPlatformConfigStore(c);restorePlatformSelection();thread.start();audio=new Handler(thread.getLooper());}
    synchronized boolean enabled(){return enabled;}
    synchronized boolean canDebugSend(){return !enabled&&!closing&&!ownsDevice&&networkJobs==0;}
    synchronized boolean canEditKey(){return !enabled&&!closing&&!ownsDevice&&networkJobs==0;}
    synchronized String status(){return status;}
    synchronized String transcript(){return transcript;}
    synchronized AgentPlatform platform(){return platformConfig.platform();}
    synchronized String platformLabel(){return platform().label;}
    synchronized String agentState(){return agentClient==null?"未连接":agentClient.state();}
    synchronized String paseoState(){return agentState();}
    synchronized String diagnostics(){return "收到唤醒 "+wakeCount+" 次 · 收到音频 "+audioCount+" 帧\n录音控制：提交 "+controlRequests+"，成功 "+controlOk+"，失败 "+controlFailed+"\n"+platformLabel()+"："+agentState()+" · 当前会话 "+(currentAgentTitle.isEmpty()?"未选择":currentAgentTitle);}
    private void trace(String event){DemoTrace.record("round="+generation+" "+event);}
    synchronized void savePaseo(String url,String password)throws Exception{if(!canEditKey())throw new IllegalStateException("请先关闭模式");paseoConfig.save(url,password);}
    synchronized void clearPaseoPassword(){if(!canEditKey())throw new IllegalStateException("请先关闭模式");paseoConfig.clearPassword();}
    synchronized String paseoUrl(){return paseoConfig.url();}
    void testPaseo()throws Exception{PaseoClient c=new PaseoClient(paseoConfig.read());try{c.connect();c.listAgents(1);synchronized(this){status="Paseo 连接测试成功";}}finally{c.close();}}
    synchronized List<PaseoConfigStore.Source> paseoSources(){return paseoConfig.sources();}
    synchronized PaseoConfigStore.Source activePaseoSource(){return paseoConfig.activeSource();}
    synchronized String savePaseoSource(String id,String name,String url,String password)throws Exception{if(!canEditKey())throw new IllegalStateException("请先关闭接管模式");String saved=paseoConfig.saveSource(id,name,url,password);resetPaseoSelection();return saved;}
    synchronized void switchPaseoSource(String id){if(!canEditKey())throw new IllegalStateException("请先关闭接管模式再切换 Paseo 源");paseoConfig.selectSource(id);resetPaseoSelection();status="已切换 Paseo 源";}
    synchronized void deletePaseoSource(String id){if(!canEditKey())throw new IllegalStateException("请先关闭接管模式");paseoConfig.deleteSource(id);resetPaseoSelection();}
    synchronized void clearPaseoSourcePassword(String id){if(!canEditKey())throw new IllegalStateException("请先关闭接管模式");paseoConfig.clearPassword(id);}
    void testPaseoSource(String id)throws Exception{PaseoClient c=new PaseoClient(paseoConfig.readSource(id));try{c.connect();c.listAgents(1);synchronized(this){status="Paseo 连接测试成功";}}finally{c.close();}}
    synchronized void selectPlatform(AgentPlatform selected){if(!canEditKey())throw new IllegalStateException("请先关闭接管模式再切换 Agent 平台");platformConfig.selectPlatform(selected);resetAgentSelection();status="已切换到 "+selected.label;}
    synchronized void saveManagedAgent(String baseUrl,String environmentId,String agentId,String title,String version,String apiKey)throws Exception{if(!canEditKey())throw new IllegalStateException("请先关闭接管模式");platformConfig.saveManaged(baseUrl,environmentId,agentId,title,version,apiKey);resetAgentSelection();status="Managed Agent 配置已保存";}
    synchronized void clearManagedAgentKey()throws Exception{if(!canEditKey())throw new IllegalStateException("请先关闭接管模式");platformConfig.clearApiKey();}
    void testManagedAgent()throws Exception{AgentSessionClient c=new ManagedAgentClient(platformConfig.readManaged());try{c.connect();c.listSessions(1);synchronized(this){status="Managed Agent 连接测试成功";}}finally{c.close();}}
    private void restorePlatformSelection(){if(platform()==AgentPlatform.PASEO){currentAgentId=paseoConfig.selectedAgent();currentWorkspaceId=paseoConfig.selectedWorkspace();}else{currentAgentId=platformConfig.selectedSession();currentWorkspaceId="";}}
    private void resetPaseoSelection(){resetAgentSelection();}
    private void resetAgentSelection(){closeAgentClient();restorePlatformSelection();currentAgentTitle=currentWorkspaceTitle="";agents=new ArrayList<>();workspaces=new ArrayList<>();answerPages=new ArrayList<>();answerPage=listPage=workspacePage=0;nextUtteranceForCodex=awaitingWorkspaceForCreate=false;}
    private void closeAgentClient(){AgentSessionClient active=agentClient;agentClient=null;agentConnected=false;if(active!=null)try{active.close();}catch(Exception ignored){}}
    private AgentSessionClient assembleAgentClient()throws Exception{return AgentPlatformAssembler.assemble(platform(),platform()==AgentPlatform.PASEO?paseoConfig.read():null,platform()==AgentPlatform.MANAGED_AGENT?platformConfig.readManaged():null);}

    synchronized void enable()throws Exception{
        if(enabled||closing||ownsDevice||networkJobs>0)throw new IllegalStateException("请先完成上一轮清理");
        keys.read();VendorTransport nextTransport=new VendorTransport();
        VendorTransport.Decoder probe=nextTransport.decoder();probe.close();
        AgentSessionClient selected=assembleAgentClient();transport=nextTransport;agentClient=selected;agentConnected=false;restorePlatformSelection();wakeCount=audioCount=controlRequests=controlOk=controlFailed=0;
        resetDuplex();enabled=true;disableRequested=false;generation++;status="已开启 · MiniMax 可联网回答，Agent 任务会转给 "+platformLabel();DemoHooks.active=this;trace("armed platform="+platform().name());
    }
    synchronized void disable(){trace("disable_requested");disableRequested=true;generation++;if(ownsDevice){cancelPreservingRecorder();closeDevice();return;}cancelLocal();finishDisabled();}
    private void finishDisabled(){enabled=false;closing=false;stopping=false;disableRequested=false;DemoHooks.active=null;transport=null;closeAgentClient();status="已关闭 · 普通语音由官方处理";}
    synchronized void finishUtterance(){if(recording)finishCapture();}

    synchronized boolean onMessage(String device,byte[] bytes){
        if(!enabled||transport==null||!transport.device.equals(device))return false;
        DemoWire packet;try{packet=DemoWire.parse(bytes);}catch(RuntimeException e){trace("packet_rejected");return ownsDevice;}
        try{
            if(packet.type==1){int wake=new JSONObject(packet.json).optInt("rc",-1);wakeCount++;
                if(wake<1||wake>3){if(ownsDevice){generation++;cancelLocal();handoffToOfficial();}return false;}
                if(disableRequested||closing||stopping||recording){status="当前轮次尚未结束，请稍后";return true;}
                if(!ownsDevice)try{transport.prepareOrdinaryWake();}catch(Exception busy){status="本次未开始收音："+busy.getMessage();return true;}
                interruptForeground();
                beginCapture();return true;
            }
            if(!ownsDevice)return false;
            if(packet.type==3){audioCount++;if(!recording)return true;try{transport.requireIdle();}catch(Exception busy){generation++;cancelLocal();handoffToOfficial();return false;}
                if(pendingFrames.size()>=48){fail("音频队列溢出，本轮已取消");return true;}final long token=generation;final byte[] frame=packet.audio;pendingFrames.add(frame);audio.post(()->decode(token,frame));return true;}
            if(packet.type==8){generation++;cancelLocal();ownsDevice=false;closing=false;stopping=false;closeTicket++;status="眼镜已退出；任务完成后仍会通知";if(disableRequested)finishDisabled();return false;}
            if(packet.type==11){if(!recording&&!stopping&&!closing){interruptForeground();beginCapture();}return true;}
            return false;
        }catch(Exception|LinkageError e){fail("眼镜链路错误："+e.getClass().getSimpleName());return true;}
    }

    private void interruptForeground(){if(request==null&&intentRequest==null&&chatRequest==null)return;trace("foreground_interrupted");cancelForegroundCalls();if(ownsDevice&&transport!=null&&transport.connected())send(13,AssistantWire.responseComplete(),ignored->{});status="已打断，正在开始下一轮";}

    private void beginCapture()throws Exception{
        resetDuplex();decoder=transport.decoder();buffer=new SpeechBuffer();generation++;final long token=generation;ownsDevice=true;recording=true;started=SystemClock.elapsedRealtime();status="正在听 · 说完停顿约 2 秒";
        captureTimeout=()->{synchronized(this){if(token==generation&&recording){if(buffer.hasSpeech())finishCapture();else{trace("initial_inactivity_timeout");generation++;cancelPreservingRecorder();closeDevice();}}}};monitor=()->watch(token);
        audio.postDelayed(captureTimeout,ASSISTANT_IDLE_TIMEOUT_MS);audio.postDelayed(monitor,1000);
        send(13,DemoWire.control(2,1),ok->{synchronized(this){if(token==generation&&recording&&!ok)fail("眼镜录音启动失败");}});
    }
    private void watch(long token){synchronized(this){if(token!=generation||!recording)return;
        if(!transport.connected()){generation++;cancelLocal();ownsDevice=false;enabled=false;DemoHooks.active=null;status="眼镜连接断开 · 模式已关闭";closeAgentClient();return;}
        try{transport.requireIdle();}catch(Exception busy){generation++;cancelLocal();handoffToOfficial();return;}
        long now=SystemClock.elapsedRealtime();if(duplexListening&&duplexSessionDeadline>0&&now>=duplexSessionDeadline){trace("duplex_inactivity_timeout");generation++;cancelPreservingRecorder();closeDevice();return;}audio.postDelayed(monitor,1000);
    }}
    private void decode(long token,byte[] frame){synchronized(this){pendingFrames.remove(frame);byte[] pcm=null;try{if(token!=generation||!recording)return;pcm=decoder.decode(frame);boolean hadSpeech=buffer.hasSpeech();boolean ended=buffer.add(pcm);if(duplexListening&&!hadSpeech&&buffer.hasSpeech()){duplexSpeechBegan=true;touchDuplex();trace("duplex_speech_began");interruptForeground();send(13,DemoWire.control(4,1),ok->{synchronized(this){if(!ok)fail("打断录音状态切换失败");}});status="已听到打断，正在收音";}if(ended){if(duplexListening){if(buffer.hasSpeech())finishDuplexUtterance();else resetDuplexBuffer();}else finishCapture();}}catch(Exception|LinkageError e){fail("音频解码失败");}finally{Arrays.fill(frame,(byte)0);if(pcm!=null)Arrays.fill(pcm,(byte)0);}}}
    private void finishCapture(){if(!recording)return;if(!buffer.hasSpeech()){pendingWav=null;recording=false;clearAudio();stopRecording(()->show("没有听清，请触发下一轮后重试","语音输入"));return;}if(captureTimeout!=null){audio.removeCallbacks(captureTimeout);captureTimeout=null;}duplexListening=true;duplexSessionDeadline=SystemClock.elapsedRealtime()+ASSISTANT_IDLE_TIMEOUT_MS;finishDuplexUtterance();}
    private void finishDuplexUtterance(){if(!recording||buffer==null||!buffer.hasSpeech())return;final byte[] wav=buffer.takeWav();resetDuplexBuffer();duplexSpeechBegan=false;touchDuplex();status="正在识别；回答期间仍可直接说话打断";send(13,DemoWire.control(4,2),ok->{synchronized(this){if(!ok){Arrays.fill(wav,(byte)0);fail("连续收音状态切换失败");return;}if(disableRequested||!enabled){Arrays.fill(wav,(byte)0);return;}startAsr(wav);}});}
    private void resetDuplexBuffer(){if(buffer!=null)buffer.clear();buffer=new SpeechBuffer();started=SystemClock.elapsedRealtime();}
    private void stopRecording(Runnable after){if(stopping)return;if(!transport.connected()){clearPendingWav();ownsDevice=false;status="眼镜连接断开";return;}
        stopping=true;status="正在识别；助手页保持打开";final long ticket=++closeTicket;
        send(13,DemoWire.control(2,2),ok->{synchronized(this){if(ticket!=closeTicket)return;stopping=false;closeTicket++;if(!ok){clearPendingWav();status="停止录音未确认，请退出后重试";return;}if(after!=null)after.run();}});
    }
    private void startAsr(byte[] wav){final long token=generation;final MinimaxAsr call=new MinimaxAsr();request=call;networkJobs++;touchDuplex();status="正在识别";
        asrTimeout=()->{synchronized(this){if(token==generation&&request==call){call.cancel();request=null;status="ASR 超时，请触发下一轮重试";}}};audio.postDelayed(asrTimeout,45000);
        network.execute(()->{try{String text=call.transcribe(keys.read(),wav);synchronized(this){if(token!=generation||!enabled||disableRequested||request!=call)return;request=null;removeAsrTimeout();transcript=text;status="已识别，正在处理";
                    send(13,AssistantWire.asrText(shortenUtf8(text,500)),ok->{synchronized(this){if(ok)handleSpeech(text);else show("识别成功，但文字回显失败","语音输入");}});
                }}catch(Exception e){synchronized(this){if(token==generation&&enabled){request=null;removeAsrTimeout();show(e instanceof java.io.IOException?e.getMessage():"ASR 失败","语音输入");}}}
            finally{Arrays.fill(wav,(byte)0);synchronized(this){networkJobs--;}}});
    }

    private void handleSpeech(String text){if(nextUtteranceForCodex&&!currentAgentId.isEmpty()){trace("route bypass=selected_agent");remember("用户："+text);nextUtteranceForCodex=false;sendQuery(text);return;}List<String> titles=new ArrayList<>();for(AgentSessionClient.Session a:agents)titles.add(a.label());List<String> workspaceTitles=new ArrayList<>();for(AgentSessionClient.CreationTarget w:workspaces)workspaceTitles.add(w.label);routeWithModel(text,titles,workspaceTitles);
    }
    private void routeWithModel(String text,List<String> titles,List<String> workspaceTitles){final long token=generation;final boolean confirming=awaitingStopConfirmation;final String context=routeContext();final MinimaxIntentRouter call=new MinimaxIntentRouter();intentRequest=call;networkJobs++;touchDuplex();status="正在结合上下文分析意图";
        network.execute(()->{VoiceCommandRouter.Match result=null;Exception failure=null;try{result=call.classify(keys.read(),text,titles,workspaceTitles,context,confirming);}catch(Exception e){failure=e;trace("model_route_failed reason="+e.getClass().getSimpleName()+" diagnostic="+call.diagnostic());}
            synchronized(this){try{if(token==generation&&enabled&&!disableRequested&&intentRequest==call){intentRequest=null;if(failure==null)applyMatch(text,result);else show("意图识别失败，请重试。","语音输入");}}finally{networkJobs--;}}});
    }
    private void applyMatch(String text,VoiceCommandRouter.Match m){trace("route kind="+m.kind+" score="+String.format(Locale.US,"%.2f",m.score));
        remember("用户："+text);if(nextUtteranceForCodex&&(m.kind==VoiceCommandRouter.Kind.CHAT||m.kind==VoiceCommandRouter.Kind.QUERY))m=new VoiceCommandRouter.Match(VoiceCommandRouter.Kind.CODEX_QUERY,0,"",1);
        if(awaitingStopConfirmation&&m.kind!=VoiceCommandRouter.Kind.CONFIRM_STOP&&m.kind!=VoiceCommandRouter.Kind.CANCEL)awaitingStopConfirmation=false;
        switch(m.kind){
            case LIST:loadAgents();break;case LIST_NEXT:showList(listPage+1);break;case LIST_PREVIOUS:showList(listPage-1);break;
            case SELECT_NUMBER:case SELECT_TITLE:selectNumber(m.number,true);break;case INSPECT_NUMBER:selectNumber(m.number,false);break;
            case LIST_WORKSPACES:loadWorkspaces(false);break;case SELECT_WORKSPACE:selectWorkspace(m.number);break;case CREATE_SESSION:createSession();break;
            case CURRENT:showCurrent();break;case LAST_REPLY:showLast();break;case ANSWER_NEXT:showAnswerPage(answerPage+1);break;case ANSWER_PREVIOUS:showAnswerPage(answerPage-1);break;
            case STOP:awaitingStopConfirmation=true;show("要停止当前 "+platformLabel()+" 任务吗？请说“确认停止”或“取消操作”。","停止确认");break;
            case CONFIRM_STOP:awaitingStopConfirmation=false;stopCurrent();break;case CANCEL:awaitingStopConfirmation=false;show("已取消操作。","命令");break;
            case EXIT:exitAssistant();break;
            case CODEX_QUERY:nextUtteranceForCodex=false;sendQuery(text);break;
            case CHAT:case QUERY:nextUtteranceForCodex=false;sendChat(text);break;
            default:show("没有理解这个操作，请换一种说法。","语音输入");
        }
    }
    private String routeContext(){StringBuilder b=new StringBuilder();b.append("Agent 平台：").append(platformLabel()).append("\n当前会话：").append(currentAgentTitle.isEmpty()?"未选择":currentAgentTitle).append("\n当前创建目标：").append(currentWorkspaceTitle.isEmpty()?"未选择":currentWorkspaceTitle).append("\n下一句是否应发送给当前 Agent 会话：").append(nextUtteranceForCodex).append("\n是否等待为新会话选择创建目标：").append(awaitingWorkspaceForCreate).append("\n最近交互：");for(String turn:recentTurns)b.append('\n').append(turn);return b.toString();}
    private void remember(String value){String clean=shorten(value.replace('\n',' '),180);recentTurns.addLast(clean);while(recentTurns.size()>10)recentTurns.removeFirst();}
    private void exitAssistant(){trace("voice_exit");status="正在退出眼镜助手";generation++;if(ownsDevice){cancelPreservingRecorder();closeDevice();}else status="助手已退出 · TurboIO 接管保持开启";}
    private synchronized AgentSessionClient ensureAgentClient()throws Exception{if(agentClient==null)agentClient=assembleAgentClient();if(agentConnected)return agentClient;try{agentClient.connect();agentConnected=true;return agentClient;}catch(Exception e){closeAgentClient();throw e;}}
    private void loadAgents(){runAgent("正在读取会话",()->{List<AgentSessionClient.Session> found=ensureAgentClient().listSessions(20);synchronized(this){agents=found;listPage=0;resolveCurrentTitle();return listText();}});}
    private void resolveCurrentTitle(){for(AgentSessionClient.Session a:agents)if(a.id.equals(currentAgentId)){currentAgentTitle=a.label();return;}}
    private String listText(){if(agents.isEmpty())return"没有活动会话。可以说“新建会话”，或先在 "+platformLabel()+" 管理端创建。";int pages=(agents.size()+4)/5;listPage=Math.max(0,Math.min(listPage,pages-1));int start=listPage*5,end=Math.min(start+5,agents.size());StringBuilder b=new StringBuilder("会话 ").append(listPage+1).append('/').append(pages).append("：");for(int i=start;i<end;i++){AgentSessionClient.Session a=agents.get(i);b.append('\n').append(i+1).append(". ").append(shorten(a.label(),26)).append(" · ").append(statusLabel(a.status));}return b.append("\n可说：继续第二个，或查看第三个详情。").toString();}
    private void showList(int page){if(agents.isEmpty()){loadAgents();return;}listPage=page;show(listText(),"会话列表");}
    private void selectNumber(int number,boolean select){if(number<1||number>agents.size()){show("编号不在刚才的列表中，请先说“列出会话”。","会话");return;}AgentSessionClient.Session a=agents.get(number-1);if(select){currentAgentId=a.id;currentAgentTitle=a.label();persistSession(a.id);nextUtteranceForCodex=true;show("已选择："+a.label()+"。下一句话会直接发送到这个 Agent 会话。","会话");}else show("会话："+a.label()+"\n状态："+statusLabel(a.status)+"\nAgent："+a.provider+"\n上下文："+shorten(a.context,80),"会话详情");}
    private void loadWorkspaces(boolean createAfter){awaitingWorkspaceForCreate=createAfter;runAgent("正在读取创建目标",()->{List<AgentSessionClient.CreationTarget> found=ensureAgentClient().listCreationTargets(30);synchronized(this){workspaces=found;workspacePage=0;resolveCurrentWorkspace();if(createAfter&&platform()==AgentPlatform.MANAGED_AGENT&&found.size()==1){awaitingWorkspaceForCreate=false;createInWorkspace(found.get(0));return"";}return workspaceText();}});}
    private void resolveCurrentWorkspace(){currentWorkspaceTitle="";for(AgentSessionClient.CreationTarget w:workspaces)if(w.id.equals(currentWorkspaceId)){currentWorkspaceTitle=w.label;return;}}
    private String workspaceText(){if(workspaces.isEmpty())return platform()==AgentPlatform.PASEO?"Paseo 中没有可用工作区，请先在电脑或 Paseo App 添加工程。":"Managed Agent 配置中没有可用的 Environment/Agent。";int pages=(workspaces.size()+4)/5;workspacePage=Math.max(0,Math.min(workspacePage,pages-1));int start=workspacePage*5,end=Math.min(start+5,workspaces.size());StringBuilder b=new StringBuilder(platform()==AgentPlatform.PASEO?"工作区 ":"创建目标 ").append(workspacePage+1).append('/').append(pages).append("：");for(int i=start;i<end;i++){AgentSessionClient.CreationTarget w=workspaces.get(i);b.append('\n').append(i+1).append(". ").append(shorten(w.label,24)).append(" · ").append(shorten(w.detail,18));}return b.append(awaitingWorkspaceForCreate?"\n请说：选择第几个。选中后会自动新建会话。":"\n可说：选择第二个，或新建会话。").toString();}
    private void selectWorkspace(int number){if(number<1||number>workspaces.size()){show("创建目标编号无效，请先列出创建目标。","创建目标");return;}AgentSessionClient.CreationTarget w=workspaces.get(number-1);currentWorkspaceId=w.id;currentWorkspaceTitle=w.label;if(platform()==AgentPlatform.PASEO)paseoConfig.selectWorkspace(w.id);if(awaitingWorkspaceForCreate){awaitingWorkspaceForCreate=false;createInWorkspace(w);}else show("已选择创建目标："+w.label+"。可以说“新建会话”。","创建目标");}
    private void createSession(){if(platform()==AgentPlatform.MANAGED_AGENT){loadWorkspaces(true);return;}if(workspaces.isEmpty()){loadWorkspaces(true);return;}AgentSessionClient.CreationTarget selected=null;for(AgentSessionClient.CreationTarget w:workspaces)if(w.id.equals(currentWorkspaceId)){selected=w;break;}if(selected==null){awaitingWorkspaceForCreate=true;show(workspaceText(),"新建会话");return;}createInWorkspace(selected);}
    private void createInWorkspace(AgentSessionClient.CreationTarget workspace){networkJobs++;status="正在“"+workspace.label+"”新建 Agent 会话";network.execute(()->{try{AgentSessionClient.Session created=ensureAgentClient().createSession(workspace);synchronized(this){agents.add(0,created);currentAgentId=created.id;currentAgentTitle=created.label();persistSession(created.id);nextUtteranceForCodex=true;finishAssistantForBackground("会话已创建，可以直接说任务。","新建会话");}}catch(Exception e){synchronized(this){show(safeError(e),"新建会话");}}finally{synchronized(this){networkJobs--;}}});}
    private void persistSession(String id){if(platform()==AgentPlatform.PASEO)paseoConfig.selectAgent(id);else platformConfig.selectSession(id);}
    private void showCurrent(){show(currentAgentId.isEmpty()?"尚未选择会话。请说“列出会话”。":"当前会话："+(currentAgentTitle.isEmpty()?shorten(currentAgentId,12):currentAgentTitle),"当前会话");}
    private void showLast(){List<String> cached=answersByAgent.get(currentAgentId);if(cached!=null){answerPages=cached;answerSource=platformLabel()+" 回复";showAnswerPage(0);return;}if(currentAgentId.isEmpty()){showCurrent();return;}final String id=currentAgentId;runAgent("正在读取最后回复",()->{String value=ensureAgentClient().lastAssistant(id);synchronized(this){cacheAnswer(id,value);}return value;});}
    private void stopCurrent(){if(currentAgentId.isEmpty()){showCurrent();return;}runAgent("正在停止任务",()->{ensureAgentClient().cancel(currentAgentId);return"已请求停止当前任务。";});}
    private interface AgentWork{String run()throws Exception;}
    private void runAgent(String waiting,AgentWork work){networkJobs++;status=waiting;network.execute(()->{try{String value=work.run();synchronized(this){if(value!=null&&!value.trim().isEmpty())show(value,waiting);}}catch(Exception e){synchronized(this){show(safeError(e),platformLabel());}}finally{synchronized(this){networkJobs--;}}});}

    private void sendQuery(String query){if(currentAgentId.isEmpty()&&platform()!=AgentPlatform.MANAGED_AGENT){show("还没有选择会话。先说“列出会话”，再说“继续第一个”。",platformLabel());return;}
        final String initialAgent=currentAgentId,initialTitle=currentAgentTitle;
        final String wrapped="[RayNeo 眼镜回复要求]\n先给结论；最多 4 个短要点；正文尽量不超过 350 个中文字符；不要输出表格、长代码或长日志；必要时说明可在 "+platformLabel()+" 管理端查看详情。\n\n用户语音："+query;
        networkJobs++;status=initialAgent.isEmpty()?"正在创建并提交 Managed Agent 任务":"正在发送到 "+(initialTitle.isEmpty()?"当前会话":initialTitle);network.execute(()->{String agent=initialAgent,title=initialTitle;boolean submitted=false;try{
            AgentSessionClient client=ensureAgentClient();if(agent.isEmpty()){List<AgentSessionClient.CreationTarget> targets=client.listCreationTargets(1);if(targets.isEmpty())throw new IllegalStateException("Managed Agent 没有可用的创建目标");AgentSessionClient.Session created=client.createSession(targets.get(0));agent=created.id;title=created.label();synchronized(this){agents.add(0,created);currentAgentId=agent;currentAgentTitle=title;persistSession(agent);}}
            client.send(agent,wrapped);submitted=true;final String acceptedAgent=agent,acceptedTitle=title;synchronized(this){trace("agent_task_submitted platform="+platform().name());finishAssistantForBackground("任务已提交到“"+(acceptedTitle.isEmpty()?shorten(acceptedAgent,10):acceptedTitle)+"”，完成后会通过通知返回，可以继续说话。",platformLabel());}
            AgentSessionClient.Finish done=client.waitForFinish(agent,30*60*1000L);boolean failed="failed".equals(done.status)||"error".equals(done.status);String result=failed?done.error:done.lastMessage;if(result==null||result.trim().isEmpty())result=failed?done.lastMessage:done.error;if(result==null||result.trim().isEmpty())result="任务结束，状态："+done.status;String notification;try{notification=NotificationResultFormatter.format(keys.read(),result);}catch(Exception keyFailure){notification=NotificationResultFormatter.formatLocally(result);}
            final boolean taskFailed=failed;synchronized(this){if(enabled&&!disableRequested)publish(agent,platformLabel()+(taskFailed?" 失败":" 完成"),result,notification,query);}
        }catch(Exception e){synchronized(this){if(enabled&&!disableRequested){String error=safeError(e);if(submitted)publish(agent,platformLabel()+" 失败",error,error,query);else show(error,platformLabel());}}}finally{synchronized(this){networkJobs--;}}});
    }
    private void finishAssistantForBackground(String text,String query){show(text,query);}
    private void sendChat(String query){final long token=generation;final String context=recentContextForChat(query);final MinimaxResponsesClient call=new MinimaxResponsesClient();chatRequest=call;streamQuery=query;streamRoundId=UUID.randomUUID().toString();pendingStreamText="";streamSent=false;networkJobs++;touchDuplex();status="MiniMax 正在思考，必要时会联网搜索";network.execute(()->{try{String result=call.complete(keys.read(),query,context,cumulative->{synchronized(this){if(token==generation&&enabled&&!disableRequested&&chatRequest==call)queueStreamText(token,cumulative);}});synchronized(this){if(token==generation&&enabled&&!disableRequested&&chatRequest==call){chatRequest=null;finishStreamAnswer(result);}}}catch(Exception e){synchronized(this){if(token==generation&&enabled&&chatRequest==call){chatRequest=null;clearStream();show(safeError(e),"MiniMax");}}}finally{synchronized(this){networkJobs--;}}});}
    private String recentContextForChat(String query){List<String> turns=new ArrayList<>(recentTurns);String current=shorten(("用户："+query).replace('\n',' '),180);if(!turns.isEmpty()&&current.equals(turns.get(turns.size()-1)))turns.remove(turns.size()-1);StringBuilder b=new StringBuilder();for(String turn:turns)b.append(turn).append('\n');return b.toString();}
    private void queueStreamText(long token,String cumulative){pendingStreamText=cumulative;touchDuplex();if(streamFlush!=null)return;long delay=Math.max(0,260-(SystemClock.elapsedRealtime()-lastStreamAt));streamFlush=()->{synchronized(this){if(token!=generation||chatRequest==null){streamFlush=null;return;}streamFlush=null;flushPartialAnswer();}};audio.postDelayed(streamFlush,delay);}
    private void flushPartialAnswer(){String text=latestWindow(pendingStreamText,420);if(text.isEmpty())return;lastStreamAt=SystemClock.elapsedRealtime();touchDuplex();streamSent=true;status="MiniMax 正在流式回答";send(13,AssistantWire.answer(text,shortenUtf8(streamQuery,500),streamRoundId,false),ignored->{});}
    private void finishStreamAnswer(String result){if(streamFlush!=null)audio.removeCallbacks(streamFlush);streamFlush=null;if(!streamSent&&!pendingStreamText.isEmpty())flushPartialAnswer();answerPages=splitUtf8(result,420);answerPage=answerPages.size()-1;answerSource="MiniMax 回复";String text=answerPages.get(answerPage)+(answerPages.size()>1?"\n（"+(answerPage+1)+"/"+answerPages.size()+"，可说上一段）":"");touchDuplex();status=shorten(result.replace('\n',' '),100);remember("助手："+result);send(13,AssistantWire.answer(shortenUtf8(text,500),shortenUtf8(streamQuery,500),streamRoundId,true),ok->{if(ok)send(13,AssistantWire.responseComplete(),ignored->{synchronized(this){keepAssistantOpen();}});});clearStreamFields();}
    static String latestWindow(String value,int max){String s=value==null?"":value.trim();if(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=max)return s;int budget=max-"…".getBytes(java.nio.charset.StandardCharsets.UTF_8).length;while(!s.isEmpty()&&s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>budget)s=s.substring(s.offsetByCodePoints(0,1));return"…"+s;}
    private void clearStream(){if(streamFlush!=null)audio.removeCallbacks(streamFlush);streamFlush=null;clearStreamFields();}
    private void clearStreamFields(){pendingStreamText="";streamQuery="";streamRoundId="";streamSent=false;}
    private void cacheAnswer(String agent,String text){List<String> pages=splitUtf8(text,420);answersByAgent.put(agent,pages);if(agent.equals(currentAgentId)){answerPages=pages;answerPage=0;answerSource=platformLabel()+" 回复";}}
    private void showAnswerPage(int page){if(answerPages.isEmpty()){show("还没有可显示的回复。",answerSource);return;}answerPage=Math.max(0,Math.min(page,answerPages.size()-1));String suffix=answerPages.size()>1?"\n（"+(answerPage+1)+"/"+answerPages.size()+"，可说下一段或上一段）":"";show(answerPages.get(answerPage)+suffix,answerSource);}
    private void publish(String agent,String title,String fullText,String notificationText,String query){cacheAnswer(agent,fullText);trace("agent_result_notification");notify(title,notificationText);status=title+" · "+shorten(notificationText,60);}
    private void show(String text,String query){touchDuplex();status=shorten(text.replace('\n',' '),100);remember("助手："+text);if(!ownsDevice||transport==null||!transport.connected()){notify("Turbo IO",text);return;}String id=UUID.randomUUID().toString();send(13,AssistantWire.answer(shortenUtf8(text,500),shortenUtf8(query,500),id),ok->{if(ok)send(13,AssistantWire.responseComplete(),ignored->{synchronized(this){keepAssistantOpen();}});});}
    private void keepAssistantOpen(){touchDuplex();if(duplexListening&&recording&&!duplexSpeechBegan)status=status+" · 可直接追问或说退出";}
    private void touchDuplex(){if(duplexListening&&recording)duplexSessionDeadline=SystemClock.elapsedRealtime()+ASSISTANT_IDLE_TIMEOUT_MS;}
    private void notify(String title,String text){try{NotificationWire.Packet p=NotificationWire.notification(title,shorten(text,480));send(21,p.bytes,ok->{});}catch(Exception ignored){}}

    private void closeDevice(){if(closing)return;if(transport==null||!transport.connected()){ownsDevice=false;clearPendingWav();if(disableRequested)finishDisabled();return;}
        closing=true;stopping=false;status="正在退出眼镜助手";final long ticket=++closeTicket;final boolean needStop=recording;recording=false;resetDuplex();clearAudio();final boolean[] ack={!needStop,false};
        VendorTransport.Completion done=ok->{synchronized(this){if(ticket!=closeTicket)return;if(!ok){closing=false;status="退出指令未确认，请重试";return;}if(!ack[0]||!ack[1])return;ownsDevice=false;closing=false;closeTicket++;if(disableRequested)finishDisabled();else status="助手已退出 · TurboIO 接管保持开启";}};
        if(needStop)send(13,DemoWire.control(2,2),ok->{ack[0]=ok;done.done(ok);});send(13,DemoWire.control(7,1),ok->{ack[1]=ok;done.done(ok);});
    }
    private void handoffToOfficial(){resetDuplex();ownsDevice=false;closing=false;stopping=false;closeTicket++;status="已让出语音，官方功能接管";if(disableRequested)finishDisabled();}
    private void send(int business,byte[] payload,VendorTransport.Completion callback){if(transport==null){callback.done(false);return;}final int messageType=DemoWire.parse(payload).type;final boolean control=business==13&&messageType==2;if(control)controlRequests++;
        java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean();final Runnable[] timeout={null};VendorTransport.Completion done=ok->{if(once.compareAndSet(false,true)){audio.removeCallbacks(timeout[0]);synchronized(this){if(control){if(ok)controlOk++;else controlFailed++;}trace("send_result business="+business+" type="+messageType+" ok="+ok);}callback.done(ok);}};
        timeout[0]=()->done.done(false);audio.postDelayed(timeout[0],5000);try{transport.send(business,payload,done);}catch(Exception e){done.done(false);}
    }
    private void fail(String message){trace("failed reason="+message);generation++;if(ownsDevice){cancelPreservingRecorder();closeDevice();}else{cancelLocal();status=message;}}
    private void clearPendingWav(){if(pendingWav!=null){Arrays.fill(pendingWav,(byte)0);pendingWav=null;}}
    private void removeAsrTimeout(){if(asrTimeout!=null){audio.removeCallbacks(asrTimeout);asrTimeout=null;}}
    private void cancelForegroundCalls(){removeAsrTimeout();if(request!=null){request.cancel();request=null;}if(intentRequest!=null){intentRequest.cancel();intentRequest=null;}if(chatRequest!=null){chatRequest.cancel();chatRequest=null;}clearStream();}
    private void cancelLocal(){recording=false;resetDuplex();clearAudio();clearPendingWav();cancelForegroundCalls();}
    private void cancelPreservingRecorder(){boolean active=recording;clearAudio();clearPendingWav();cancelForegroundCalls();recording=active;}
    private void clearAudio(){if(captureTimeout!=null)audio.removeCallbacks(captureTimeout);if(monitor!=null)audio.removeCallbacks(monitor);captureTimeout=monitor=null;for(byte[] b:pendingFrames)Arrays.fill(b,(byte)0);pendingFrames.clear();if(decoder!=null){decoder.close();decoder=null;}if(buffer!=null){buffer.clear();buffer=null;}}
    private void resetDuplex(){duplexListening=false;duplexSpeechBegan=false;duplexSessionDeadline=0;}
    private static String statusLabel(String value){if("running".equals(value))return"运行中";if("initializing".equals(value))return"启动中";if("error".equals(value))return"失败";if("closed".equals(value))return"已关闭";return"空闲";}
    private static String safeError(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?"操作失败："+e.getClass().getSimpleName():"操作失败："+shorten(m,120);}
    private static String shorten(String value,int max){String s=value==null?"":value;if(s.codePointCount(0,s.length())<=max)return s;return s.substring(0,s.offsetByCodePoints(0,max-1))+"…";}
    private static String shortenUtf8(String value,int max){String s=value==null?"":value;while(s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>max&&!s.isEmpty())s=s.substring(0,s.offsetByCodePoints(0,s.codePointCount(0,s.length())-1));return s;}
    private static List<String> splitUtf8(String value,int max){String s=value==null?"":value.trim();List<String> out=new ArrayList<>();while(!s.isEmpty()){int cut=s.length();while(cut>0&&s.substring(0,cut).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>max)cut=s.offsetByCodePoints(0,s.codePointCount(0,cut)-1);if(cut<s.length()){int natural=Math.max(s.lastIndexOf('\n',cut),Math.max(s.lastIndexOf('。',cut),s.lastIndexOf('；',cut)));if(natural>cut/2)cut=natural+1;}out.add(s.substring(0,cut).trim());s=s.substring(cut).trim();}if(out.isEmpty())out.add("任务已结束，没有可显示的回复。");return out;}
}

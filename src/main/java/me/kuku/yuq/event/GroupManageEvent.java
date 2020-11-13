package me.kuku.yuq.event;

import com.IceCreamQAQ.Yu.annotation.Event;
import com.IceCreamQAQ.Yu.annotation.EventListener;
import com.IceCreamQAQ.Yu.cache.EhcacheHelp;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.icecreamqaq.yuq.FunKt;
import com.icecreamqaq.yuq.event.GroupMessageEvent;
import com.icecreamqaq.yuq.message.*;
import me.kuku.yuq.entity.GroupEntity;
import me.kuku.yuq.entity.QQEntity;
import me.kuku.yuq.logic.QQAILogic;
import me.kuku.yuq.service.GroupService;
import me.kuku.yuq.service.QQService;
import me.kuku.yuq.utils.BotUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;

@EventListener
public class GroupManageEvent {
    @Inject
    private GroupService groupService;
    @Inject
    private QQService qqService;
    @Inject
    private QQAILogic qqaiLogic;
    @Inject
    @Named("CommandCountOnTime")
    public EhcacheHelp<Integer> eh;

    @Event
    public void inter(GroupMessageEvent e) throws IOException {
        GroupEntity groupEntity = groupService.findByGroup(e.getGroup().getId());
        if (groupEntity == null) return;
        if (groupEntity.getWhiteJsonArray().contains(String.valueOf(e.getSender().getId()))) return;
        Message message = e.getMessage();
        String str;
        try {
            str = message.toPath().get(0);
        }catch (IllegalStateException ex){
            str = null;
        }
        if (str != null){
            JSONArray interceptJsonArray = groupEntity.getInterceptJsonArray();
            for (int i = 0; i < interceptJsonArray.size(); i++){
                String intercept = interceptJsonArray.getString(i);
                if (str.contains(intercept)){
                    e.cancel = true;
                    break;
                }
            }
        }
        if (!e.getGroup().getBot().isAdmin()) return;
        QQEntity qqEntity = qqService.findByQQAndGroup(e.getSender().getId(), e.getGroup().getId());
        if (qqEntity == null) qqEntity = new QQEntity(e.getSender().getId(), groupEntity);
        JSONArray violationJsonArray = groupEntity.getViolationJsonArray();
        int code = 0;
        String vio = null;
        out:for (int i = 0; i < violationJsonArray.size(); i++){
            String violation = violationJsonArray.getString(i);
            String nameCard = e.getSender().getNameCard();
            if (nameCard.contains(violation)) {
                code = 3;
                vio = violation;
                break;
            }
            for (MessageItem item: message.getBody()){
                if (item instanceof Text){
                    Text text = (Text) item;
                    if (text.getText().contains(violation)) code = 1;
                }else if (item instanceof Image){
                    Image image = (Image) item;
                    String result = qqaiLogic.generalOCR(image.getUrl());
                    if (result.contains(violation)) code = 1;
                    boolean b = qqaiLogic.pornIdentification(image.getUrl());
                    if (b) code = 2;
                }else if (item instanceof XmlEx){
                    XmlEx xmlEx = (XmlEx) item;
                    if (xmlEx.getValue().contains(violation)) code = 1;
                }else if (item instanceof JsonEx){
                    JsonEx jsonEx = (JsonEx) item;
                    if (jsonEx.getValue().contains(violation)) code = 1;
                }
                if (code != 0){
                 vio = violation;
                 break out;
                }
            }
        }
        if (code != 0){
            qqEntity.setViolationCount(qqEntity.getViolationCount() + 1);
            if (qqEntity.getViolationCount() < groupEntity.getMaxViolationCount()){
                StringBuilder sb = new StringBuilder();
                if (code == 2) sb.append("检测到色情图片。").append("\n");
                else if (code == 1) sb.append("检测到违规词\"").append(vio).append("\"。").append("\n");
                else sb.append("检测到违规去群名片\"").append(vio).append("\"。").append("\n");
                sb.append("您当前的违规次数为").append(qqEntity.getViolationCount())
                        .append("次，累计违规").append(groupEntity.getMaxViolationCount())
                        .append("次会被移除本群哦！！");
                e.getGroup().sendMessage(FunKt.getMif().at(qqEntity.getQq()).plus(sb.toString()));
            }else {
                e.getSender().kick("违规次数已上限！！");
                e.getGroup().sendMessage(Message.Companion.toMessage(
                        qqEntity.getQq() + "违规次数已达上限，送飞机票一张！！"
                ));
            }
        }
    }

    @Event
    public void qa(GroupMessageEvent e){
        GroupEntity groupEntity = groupService.findByGroup(e.getGroup().getId());
        if (groupEntity == null) return;
        Message message = e.getMessage();
        if (message.toPath().size() == 0) return;
        if ("删问答".equals(message.toPath().get(0))) return;
        String str;
        try {
            str = Message.Companion.firstString(message);
        }catch (IllegalStateException ex){
            return;
        }
        JSONArray qaJsonArray = groupEntity.getQaJsonArray();
        for (int i = 0; i < qaJsonArray.size(); i++){
            JSONObject jsonObject = qaJsonArray.getJSONObject(i);
            String type = jsonObject.getString("type");
            String q = jsonObject.getString("q");
            boolean status = false;
            if ("ALL".equals(type)){
                if (str.equals(q)) status = true;
            }else if (str.contains(jsonObject.getString("q"))) status = true;
            if (status){
                Integer maxCount = groupEntity.getMaxCommandCountOnTime();
                if (maxCount == null) maxCount = -1;
                if (maxCount > 0){
                    String key = e.getSender().getId() + q;
                    Integer num = eh.get(key);
                    if (num == null) num = 0;
                    if (num >= maxCount) return;
                    eh.set(key, ++num);
                }
                JSONArray jsonArray = jsonObject.getJSONArray("a");
                e.getGroup().sendMessage(BotUtils.jsonArrayToMessage(jsonArray));
            }
        }
    }
}
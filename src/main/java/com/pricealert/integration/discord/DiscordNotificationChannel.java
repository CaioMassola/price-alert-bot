package com.pricealert.integration.discord;
import com.pricealert.integration.NotificationChannel;
import com.pricealert.domain.alert.PriceAlert;
import org.springframework.stereotype.Component;
import java.util.*;
import java.math.BigDecimal;
@Component
public class DiscordNotificationChannel implements NotificationChannel {
    private final DiscordWebhookClient client;
    public DiscordNotificationChannel(DiscordWebhookClient client) { this.client=client; }
    public boolean configured() { return client.configured(); }
    public void send(PriceAlert alert) { client.send(payload(alert)); }
    @Override public void send(PriceAlert alert, boolean introduction) {
        var message=new LinkedHashMap<String,Object>(payload(alert));
        if(introduction) message.put("content","Olá, soldados! Tudo bem? Encontrei novas promoções! 💜");
        client.send(message);
    }
    public Map<String,Object> payload(PriceAlert alert) {
        var product=alert.product(); var analysis=alert.analysis();
        Map<String,Object> embed=new LinkedHashMap<>();
        embed.put("title",shorten(product.name(),256)); embed.put("url",product.url()); embed.put("color",0x2ecc71);
        embed.put("description","Motivo: "+analysis.reason()+"\nPreço e estoque podem mudar. Confira condições de pagamento e frete na loja.");
        List<Map<String,Object>> fields=new ArrayList<>();
        field(fields,"Loja",product.store().name()); field(fields,"Preço anunciado",brl(product.currentPrice()));
        if(product.store()==com.pricealert.domain.store.Store.KABUM) field(fields,"Pagamento","Preço à vista quando informado pela KaBuM; confira Pix/boleto e frete.");
        if(analysis.previousPrice()!=null) field(fields,"Preço observado anterior",brl(analysis.previousPrice()));
        if(product.originalPrice()!=null) field(fields,"Referência da loja",brl(product.originalPrice())+" ("+analysis.storeDiscount()+"%)");
        if(analysis.historicalAverage()!=null) field(fields,"Média observada em 30 dias",brl(analysis.historicalAverage()));
        if(analysis.historicalLowest()!=null) field(fields,"Menor preço anterior",brl(analysis.historicalLowest()));
        if(analysis.sufficientHistory()) field(fields,"Desconto histórico",analysis.historicalDiscount()+"%");
        else field(fields,"Histórico","Ainda insuficiente para confirmar o desconto anunciado.");
        if(product.coupon()!=null) {
            field(fields,"Cupom publicado",product.coupon().code()+" — sujeito às regras da loja");
            if(product.coupon().discountPercentage()!=null || product.coupon().discountValue()!=null)
                field(fields,"Estimativa com cupom",brl(alert.effectivePrice())+" (sem frete)");
            else field(fields,"Condições do cupom","Valor e elegibilidade não informados; confira na loja.");
        }
        embed.put("fields",fields);
        if(product.imageUrl()!=null) embed.put("thumbnail",Map.of("url",product.imageUrl()));
        embed.put("timestamp",product.collectedAt().toString());
        return Map.of("allowed_mentions",Map.of("parse",List.of()),"embeds",List.of(embed));
    }
    private void field(List<Map<String,Object>> fields,String name,String value) {
        fields.add(Map.of("name",name,"value",shorten(value,1024),"inline",true));
    }
    private String brl(BigDecimal value) { return "R$ "+value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString().replace('.',','); }
    private String shorten(String text,int length) { return text.substring(0,Math.min(length,text.length())); }
}


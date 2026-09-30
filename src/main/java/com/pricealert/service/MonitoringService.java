package com.pricealert.service;
import com.pricealert.monitor.*;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.store.Store;
import com.pricealert.repository.TrackedProductRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
@Service
public class MonitoringService {
    private static final Logger log=LoggerFactory.getLogger(MonitoringService.class);
    private final List<StoreMonitor> monitors;
    private final TrackedProductRepository tracked;
    private final ProductService products;
    private final MonitorConfig config;
    private final Deque<Job> priority=new ArrayDeque<>(), discovery=new ArrayDeque<>();
    private final Set<String> queued=new HashSet<>();
    private final Map<Store,StoreHealth> health=new ConcurrentHashMap<>();
    private int queryIndex;
    private boolean discoveryRunning;
    public MonitoringService(List<StoreMonitor> monitors,TrackedProductRepository tracked,ProductService products,MonitorConfig config) {
        this.monitors=monitors.stream().sorted(Comparator.comparing(m->m.getStore().ordinal())).toList();
        this.tracked=tracked; this.products=products; this.config=config;
        monitors.forEach(m->health.put(m.getStore(),new StoreHealth(m.getStore(),"NOT_CHECKED",null,null,0,null,null)));
    }
    public synchronized void monitorTrackedProducts() {
        for(var product:tracked.findByActiveTrueOrderByIdAsc())
            add(priority,new Job(product.store,MonitorRequest.product(product.url),product.targetPrice,true));
    }
    public synchronized void discoverPromotions() {
        if(!config.promotionsEnabled() || discoveryRunning || !discovery.isEmpty()) return;
        // Mix four consecutive terms from the category-interleaved list; never accumulate rounds.
        for(int i=0;i<Math.min(4,config.queries().size());i++) {
            String query=config.queries().get(queryIndex);
            queryIndex=(queryIndex+1)%config.queries().size();
            for(var monitor:monitors) add(discovery,new Job(monitor.getStore(),MonitorRequest.search(
                monitor.getStore().isGameStore()?"promotions":query),null,false));
        }
    }
    private void add(Deque<Job> queue,Job job) {
        if(queued.add(job.key())) queue.addLast(job);
    }
    public void runNext() {
        Job job;
        synchronized(this) {
            job=priority.isEmpty()?discovery.pollFirst():priority.pollFirst();
            if(job!=null && !job.tracked) discoveryRunning=true;
        }
        if(job==null) return;
        try {
            log.info("Monitoring started store={} mode={}",job.store,job.tracked?"tracked":"discovery");
            StoreMonitor monitor=monitors.stream().filter(m->m.getStore()==job.store).findFirst().orElseThrow();
            var snapshots=monitor.searchProducts(job.request);
            int saved=0;
            for(var snapshot:snapshots) {
                products.accept(snapshot,job.target,job.tracked); saved++;
            }
            health.put(job.store,new StoreHealth(job.store,"OK",Instant.now(),null,saved,null,null));
            log.info("Monitoring completed store={} products={}",job.store,saved);
        } catch(StoreAccessException e) {
            health.put(job.store,new StoreHealth(job.store,"UNAVAILABLE",Instant.now(),"HTTP_OR_PARSER_"+e.status(),0,
                failureDescription(e.status()),nextStep(job.store,e.status())));
            log.warn("Monitoring failed store={} status={}",job.store,e.status());
        } catch(Exception e) {
            health.put(job.store,new StoreHealth(job.store,"ERROR",Instant.now(),"COLLECTION_OR_STORAGE_ERROR",0,
                "Falha de coleta ou persistência.", "Consulte os logs locais da aplicação."));
            log.warn("Monitoring failed store={} type={}",job.store,e.getClass().getSimpleName());
        } finally { synchronized(this) { queued.remove(job.key()); if(!job.tracked) discoveryRunning=false; } }
    }
    public List<StoreHealth> health() { return Arrays.stream(Store.values()).map(health::get).toList(); }
    private String failureDescription(int status) {
        return switch(status) {
            case 401 -> "Autenticação ausente ou expirada.";
            case 403 -> "Acesso negado pela loja; novas consultas ficam em pausa.";
            case 429 -> "Limite de requisições atingido; aguardando nova tentativa.";
            case 422 -> "A resposta não contém produtos reconhecíveis; coleta não validada.";
            case 404 -> "Produto ou recurso não encontrado.";
            case 0 -> "Falha de conexão ou tempo limite.";
            default -> status>=500?"A loja retornou uma página de erro ou está indisponível.":"Resposta da loja não utilizável.";
        };
    }
    private String nextStep(Store store,int status) {
        if(status==429) return "Aguarde o intervalo da loja; não é necessário reiniciar o bot.";
        return switch(store) {
            case STEAM, EPIC -> "Verifique disponibilidade do feed publico de promocoes e a configuracao do webhook de jogos.";
            case MERCADO_LIVRE -> "Para a API oficial, configure MERCADO_LIVRE_ACCESS_TOKEN e valide as permissões de consulta. Cadastro e token não garantem acesso ao catálogo. Veja docs/ACESSO-LOJAS.md.";
            case AMAZON -> "O coletor atual usa páginas públicas. A alternativa oficial exige aprovação no Amazon Associados e uma integração com Creators API, ainda não implementada. Veja docs/ACESSO-LOJAS.md.";
            case KABUM -> "Verifique disponibilidade da loja e compatibilidade do parser com o JSON público.";
        };
    }
    public record StoreHealth(Store store,String status,Instant checkedAt,String error,int productCount,String detail,String nextStep) {}
    private record Job(Store store,MonitorRequest request,BigDecimal target,boolean tracked) {
        String key() { return store+"|"+request; }
    }
}


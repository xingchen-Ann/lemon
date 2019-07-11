package cn.micro.lemon.filter.support;

import cn.micro.lemon.LemonInvoke;
import cn.micro.lemon.MicroConfig;
import cn.micro.lemon.dubbo.DubboLemonInvoke;
import cn.micro.lemon.filter.IFilter;
import cn.micro.lemon.filter.LemonChain;
import cn.micro.lemon.filter.LemonContext;
import lombok.extern.slf4j.Slf4j;
import org.micro.neural.extension.Extension;

import java.util.concurrent.CompletableFuture;

/**
 * Lemon Invoke Filter
 *
 * @author lry
 */
@Slf4j
@Extension(order = 100)
public class LemonInvokeFilter implements IFilter {

    private LemonInvoke lemonInvoke;

    @Override
    public void initialize(MicroConfig microConfig) {
        this.lemonInvoke = new DubboLemonInvoke();
        lemonInvoke.initialize(microConfig);
    }

    @Override
    public void doFilter(LemonChain chain, LemonContext context) throws Throwable {
        CompletableFuture<Object> future = lemonInvoke.invokeAsync(context);
        if (future == null) {
            log.error("The completable future is null by context:{}", context);
            return;
        }

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error(throwable.getMessage(), throwable);
            }

            try {
                chain.doFilter(context);
            } catch (Throwable t) {
                log.error(t.getMessage(), t);
            } finally {
                context.writeAndFlush(result);
            }
        });
    }

    @Override
    public void destroy() {
        if (lemonInvoke != null) {
            lemonInvoke.destroy();
        }
    }

}
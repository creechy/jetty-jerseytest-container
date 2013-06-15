package org.fakebelieve.jersey.test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.WebAppDescriptor.FilterDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import java.net.URI;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.Map;
import javax.servlet.DispatcherType;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Jetty JerseyTest Container.
 *
 * Provides an in-memory Jetty container that can be used by JerseyTest,
 * with some caveats.
 *
 * The built-in containers provided with JerseyTest do not have the
 * ability to configure both a servlet and a filter in a single instance.
 * This is a problem when testing some web frameworks like Spring with Spring
 * Security which do need both, which is the primary motivation for
 * developing this container.
 *
 * This can be used with the normal configuration mechanisms, by creasing a WebAppDescriptor,
 * with some care to work around some limitations built into it. Namely, when configuring a
 * servlet via the servletClass() method, WebAppDescriptor clears out any configured filters,
 * however the reverse does not occur. So as long as you call addFilter() after calling
 * servletClass() then both will be maintained (and passed into this ContainerFactory)
 *
 *
 * @author mock
 */
public class JettyTestContainerFactory implements TestContainerFactory {

    @Override
    public Class<WebAppDescriptor> supports() {
        return WebAppDescriptor.class;
    }

    @Override
    public TestContainer create(URI uri, AppDescriptor ad) throws IllegalArgumentException {
        if (!(ad instanceof WebAppDescriptor)) {
            throw new IllegalArgumentException(
                    "The application descriptor must be an instance of WebAppDescriptor");
        }

        return new JettyTestContainer(uri, (WebAppDescriptor) ad);
    }

    /**
     *
     * @author mock
     */
    private static class JettyTestContainer implements TestContainer {

        private static final Logger logger = LoggerFactory.getLogger(JettyTestContainer.class);
        private final URI baseUri;
        private final Server server;

        private JettyTestContainer(URI baseUri, WebAppDescriptor ad) {
            // handle:  context.contextPath(CONTEXT_PATH)
            // handle:  context.servletPath(SERVLET_PATH)
            this.baseUri = UriBuilder.fromUri(baseUri).path(ad.getContextPath()).path(ad.getServletPath()).build();

            // handle:
            logger.debug("jetty port = {}", baseUri.getPort());
            server = new Server(baseUri.getPort());

            // handle:  context.contextPath(CONTEXT_PATH)
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            logger.debug("context path = {}", ad.getContextPath());
            context.setContextPath(ad.getContextPath());
            server.setHandler(context);

            // handle: context.getInitParams().put("contextConfigLocation", "classpath:/test-application-context.xml");
            for (Map.Entry<String, String> param : ad.getContextParams().entrySet()) {
                logger.debug("context param = ({},{})", param.getKey(), param.getValue());
                context.getInitParams().put(param.getKey(), param.getValue());
            }

            // handle: context.addEventListener(new ContextLoaderListener());
            // handle: context.addEventListener(new RequestContextListener());
            for (Class<? extends EventListener> listener : ad.getListeners()) {
                try {
                    logger.debug("context listener = {}", listener.getName());
                    context.addEventListener(listener.newInstance());
                } catch (InstantiationException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

            // handle:  context.servletClass(SpringServlet.class)
            logger.debug("servlet class = {}", ad.getServletClass().getName());
            ServletHolder servletHolder = new ServletHolder(ad.getServletClass());

            // handle: context.initParameter(<name>, <value>);
            for (Map.Entry<String, String> param : ad.getInitParams().entrySet()) {
                logger.debug("servlet init param = ({},{})", param.getKey(), param.getValue());
                servletHolder.setInitParameter(param.getKey(), param.getValue());
            }
            // handle:  context.servletPath(SERVLET_PATH)
            logger.debug("servlet path = {}", ad.getServletPath());
            if (ad.getServletPath().endsWith("*")) {
                context.addServlet(servletHolder, ad.getServletPath());
            } else if (ad.getServletPath().endsWith("/")) {
                context.addServlet(servletHolder, ad.getServletPath() + "*");
            } else {
                context.addServlet(servletHolder, ad.getServletPath() + "/*");
            }

            // handle:  context.addFilter(DelegatingFilterProxy.class, "springSecurityFilterChain")
            for (FilterDescriptor descriptor : ad.getFilters()) {
                FilterHolder filterHolder = new FilterHolder(descriptor.getFilterClass());
                filterHolder.setName(descriptor.getFilterName());
                context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
            }
        }

        @Override
        public Client getClient() {
            return null;
        }

        @Override
        public URI getBaseUri() {
            return baseUri;
        }

        @Override
        public void start() {
            try {
                server.start();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void stop() {
            try {
                server.stop();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}

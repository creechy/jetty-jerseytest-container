jetty-jerseytest-container
==========================

An in-memory Jetty-based JerseyTest container.

Provides an in-memory Jetty container that can be used by JerseyTest,
with some caveats.

The built-in containers provided with JerseyTest do not have the
ability to configure both a servlet and a filter in a single instance.
This is a problem when testing some web frameworks like Spring with Spring
Security which do need both, which is the primary motivation for
developing this container.

This can be used with the normal configuration mechanisms, by creasing a WebAppDescriptor,
with some care to work around some limitations built into it. Namely, when configuring a
servlet via the servletClass() method, WebAppDescriptor clears out any configured filters,
however the reverse does not occur. So as long as you call addFilter() after calling
servletClass() then both will be maintained (and passed into this ContainerFactory)


    @Override
    protected AppDescriptor configure() {
        AppDescriptor app = new WebAppDescriptor.Builder("org.fakebelieve")
                .contextPath("/context")
                // jetty-specific: contextConfigLocation needs classpath: added to it
                .contextParam("contextConfigLocation", "classpath:/test-application-context.xml")
                .contextListenerClass(ContextLoaderListener.class)
                .requestListenerClass(RequestContextListener.class)
                .servletClass(SpringServlet.class)
                .servletPath("/resources")
                .initParam(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS, "com.sun.jersey.api.container.filter.PostReplaceFilter,com.sun.jersey.api.container.filter.LoggingFilter")
                .initParam(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS, "com.sun.jersey.api.container.filter.LoggingFilter")
                .initParam(JSONConfiguration.FEATURE_POJO_MAPPING, "true")
                .initParam(ServletContainer.RESOURCE_CONFIG_CLASS, "com.sun.jersey.api.core.PackagesResourceConfig")
                .initParam(PackagesResourceConfig.PROPERTY_PACKAGES, "org.fakebelieve.common.resolvers;org.fakebelieve.common.exception.mappers")
                // jetty-specific: addFilter() must come after servletClass() otherwise is null
                .addFilter(DelegatingFilterProxy.class, "springSecurityFilterChain")
                .build();

        app.getClientConfig().getSingletons().add(new JacksonJsonProvider(mapper));
        app.getClientConfig().getClasses().add(JacksonJsonProvider.class);
        app.getClientConfig().getProperties().put("logging", Boolean.TRUE);

        return app;
    }

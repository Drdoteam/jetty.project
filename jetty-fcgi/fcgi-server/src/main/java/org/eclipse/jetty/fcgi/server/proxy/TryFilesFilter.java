//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.fcgi.server.proxy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Inspired by nginx's try_files functionality.
 * <p>
 * This filter accepts the <code>files</code> init-param as a list of space-separated
 * file URIs. The special token <code>$path</code> represents the current request URL's
 * path (the portion after the context path).
 * <p>
 * Typical example of how this filter can be configured is the following:
 * <pre>
 * &lt;filter&gt;
 *     &lt;filter-name&gt;try_files&lt;/filter-name&gt;
 *     &lt;filter-class&gt;org.eclipse.jetty.fcgi.server.proxy.TryFilesFilter&lt;/filter-class&gt;
 *     &lt;init-param&gt;
 *         &lt;param-name&gt;files&lt;/param-name&gt;
 *         &lt;param-value&gt;maintenance.html $path index.php?p=$path&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 * &lt;/filter&gt;
 * </pre>
 * For a request such as <code>/context/path/to/resource.ext</code>, this filter will
 * try to serve the <code>/maintenance.html</code> file if it finds it; failing that,
 * it will try to serve the <code>/path/to/resource.ext</code> file if it finds it;
 * failing that it will forward the request to <code>index.php?p=/path/to/resource.ext</code>.
 * The last file URI specified in the list is therefore the "fallback" to which the request
 * is forwarded to in case no previous files can be found.
 * <p>
 * The files are resolved using {@link ServletContext#getResource(String)} to make sure
 * that only files visible to the application are served.
 *
 * @see FastCGIProxyServlet
 */
public class TryFilesFilter implements Filter
{
    public static final String FILES_INIT_PARAM = "files";

    private String[] files;

    @Override
    public void init(FilterConfig config) throws ServletException
    {
        String param = config.getInitParameter(FILES_INIT_PARAM);
        if (param == null)
            throw new ServletException(String.format("Missing mandatory parameter '%s'", FILES_INIT_PARAM));
        files = param.split(" ");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        for (int i = 0; i < files.length - 1; ++i)
        {
            String file = files[i];
            String resolved = resolve(httpRequest, file);

            URL url = request.getServletContext().getResource(resolved);
            if (url == null)
                continue;

            if (Files.isReadable(toPath(url)))
            {
                chain.doFilter(httpRequest, httpResponse);
                return;
            }
        }

        // The last one is the fallback
        fallback(httpRequest, httpResponse, chain, files[files.length - 1]);
    }

    private Path toPath(URL url) throws IOException
    {
        try
        {
            return Paths.get(url.toURI());
        }
        catch (URISyntaxException x)
        {
            throw new IOException(x);
        }
    }

    protected void fallback(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String fallback) throws IOException, ServletException
    {
        String resolved = resolve(request, fallback);
        request.getServletContext().getRequestDispatcher(resolved).forward(request, response);
    }

    private String resolve(HttpServletRequest request, String value)
    {
        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null)
            path += info;
        if (!path.startsWith("/"))
            path = "/" + path;
        return value.replaceAll("\\$path", path);
    }

    @Override
    public void destroy()
    {
    }
}

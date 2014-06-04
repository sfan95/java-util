package com.cedarsoftware.ncube.util;

import com.cedarsoftware.ncube.CommandCell;
import com.cedarsoftware.ncube.executor.DefaultExecutor;
import com.cedarsoftware.util.IOUtilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class CdnUrlExecutor extends DefaultExecutor
{
    private HttpServletRequest request;
    private HttpServletResponse response;

    public CdnUrlExecutor(HttpServletRequest request, HttpServletResponse response)
    {
        this.request = request;
        this.response = response;
    }

    // TODO: Copy HTTP Response headers from Apache
    public Object executeCommand(CommandCell c, Map<String, Object> ctx)
    {
        String url = c.getUrl();
        Object cmd = c.getCmd();

        // ignore local caching
        if (url != null)
        {
            if (!c.isExpanded())
            {
                c.expandUrl(url, ctx);
            }

            try
            {
                HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
                conn.setAllowUserInteraction(false);

                conn.setRequestMethod(request.getMethod() != null ? request.getMethod() : "GET");

                conn.setDoOutput(true); // true
                conn.setDoInput(true); // true
                conn.setReadTimeout(220000);
                conn.setConnectTimeout(45000);

                setupRequestHeaders(conn);

                conn.connect();

                transferToServer(conn);

                int resCode = conn.getResponseCode();

                if (resCode == 200) {
                    setupResponseHeaders(conn);
                    transferFromServer(conn);
                } else {
                    response.sendError(resCode);
                }
            } catch (Exception e) {
                try
                {
                    response.sendError(404);
                } catch (IOException ignored) {
                    //  do nothing.
                }
            }

        }

        return null;
    }

    private void transferToServer(HttpURLConnection conn) throws IOException
    {
        OutputStream out = null;
        InputStream in = null;

        try
        {
            in = request.getInputStream();
            out = new BufferedOutputStream(conn.getOutputStream(), 32768);
            IOUtilities.transfer(in, out);
        } finally {
            IOUtilities.close(in);
            IOUtilities.close(out);
        }
    }

    private void transferFromServer(HttpURLConnection conn) throws IOException
    {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new BufferedInputStream(conn.getInputStream(), 32768);
            out = response.getOutputStream();
            IOUtilities.transfer(in, out);
        } finally {
            IOUtilities.close(in);
            IOUtilities.close(out);
        }
    }

    public void setupRequestHeaders(HttpURLConnection c) {

        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements())
        {
            String key = (String) headerNames.nextElement();
            String value = request.getHeader(key);
            c.setRequestProperty(key, value);
        }
    }

    public void setupResponseHeaders(HttpURLConnection c) {

        if (response.containsHeader("Content-Length")) {
            response.addIntHeader("Content-Length", c.getContentLength());
        }

        if (response.containsHeader("Last-Modified")) {
            response.addDateHeader("Last-Modified", c.getLastModified());
        }

        if (response.containsHeader("Content-Encoding")) {
            response.addHeader("Content-Encoding", c.getContentEncoding());
        }

        if (response.containsHeader("Content-Type"))
        {
            response.addHeader("Content-Type", c.getContentType());
        }

        if (response.containsHeader("Cache-Control")) {
            response.addHeader("Cache-Control", c.getHeaderField("Cache-Control"));
        }


    }

}


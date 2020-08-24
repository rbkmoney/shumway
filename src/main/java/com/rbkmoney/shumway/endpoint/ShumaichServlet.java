package com.rbkmoney.shumway.endpoint;

import com.rbkmoney.damsel.shumaich.AccounterSrv;
import com.rbkmoney.woody.thrift.impl.http.THServiceBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet("/shumaich")
public class ShumaichServlet extends GenericServlet  {

    private Servlet thriftServlet;

    @Autowired
    private AccounterSrv.Iface shumaichServiceHandler;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        thriftServlet = new THServiceBuilder()
                .build(AccounterSrv.Iface.class, shumaichServiceHandler);
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        thriftServlet.service(req, res);
    }

}

package org.batfish.grammar.flatjuniper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.batfish.common.BatfishException;
import org.batfish.common.Warnings;
import org.batfish.common.util.CommonUtil;
import org.batfish.common.util.JuniperUtils;
import org.batfish.datamodel.AsPath;
import org.batfish.datamodel.DiffieHellmanGroup;
import org.batfish.datamodel.EncryptionAlgorithm;
import org.batfish.datamodel.ExtendedCommunity;
import org.batfish.datamodel.IcmpCode;
import org.batfish.datamodel.IcmpType;
import org.batfish.datamodel.IkeAuthenticationAlgorithm;
import org.batfish.datamodel.IkeAuthenticationMethod;
import org.batfish.datamodel.IkeProposal;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpsecAuthenticationAlgorithm;
import org.batfish.datamodel.IpsecProposal;
import org.batfish.datamodel.IpsecProtocol;
import org.batfish.datamodel.IsoAddress;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.NamedPort;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.Prefix6;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.SnmpCommunity;
import org.batfish.datamodel.SnmpHost;
import org.batfish.datamodel.SnmpServer;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.TcpFlags;
import org.batfish.datamodel.VrrpGroup;
import org.batfish.grammar.flatjuniper.FlatJuniperParser.*;
import org.batfish.representation.juniper.*;
import org.batfish.representation.juniper.BaseApplication.Term;
import org.batfish.representation.juniper.BgpGroup.BgpGroupType;

public class ConfigurationBuilder extends FlatJuniperParserBaseListener {

   private static final boolean DEFAULT_VRRP_PREEMPT = true;

   private static final int DEFAULT_VRRP_PRIORITY = 100;

   private static final AggregateRoute DUMMY_AGGREGATE_ROUTE = new AggregateRoute(
         Prefix.ZERO);

   private static final BgpGroup DUMMY_BGP_GROUP = new BgpGroup();

   private static final StaticRoute DUMMY_STATIC_ROUTE = new StaticRoute(
         Prefix.ZERO);

   private static final String F_BGP_LOCAL_AS_LOOPS =
         "protocols - bgp - group? - local-as - loops - currently we allow infinite occurences of local as";

   private static final String F_BGP_LOCAL_AS_PRIVATE =
         "protocols - bgp - group? - local-as - private";

   private static final String F_FIREWALL_TERM_THEN_ROUTING_INSTANCE =
         "firewall - filter - term - then - routing-instance";

   private static final String F_IPV6 = "ipv6 - other";

   private static final String F_PERMIT_TUNNEL =
         "security - policies - from-zone - to-zone - policy - then - permit - tunnel";

   private static final String F_POLICY_TERM_THEN_NEXT_HOP =
         "policy-statement - term - then - next-hop";

   private DhcpRelayGroup _currentDhcpRelayGroup;

   @Override
   public void enterFo_dhcp_relay(Fo_dhcp_relayContext ctx) {
      _currentDhcpRelayGroup = _currentRoutingInstance.getDhcpRelayGroups()
            .computeIfAbsent(DhcpRelayGroup.MASTER_DHCP_RELAY_GROUP_NAME,
                  k -> new DhcpRelayGroup(k));
   }

   @Override
   public void exitFod_active_server_group(Fod_active_server_groupContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentDhcpRelayGroup.setActiveServerGroup(name);
      _currentDhcpRelayGroup.setActiveServerGroupLine(line);
   }

   @Override
   public void enterFod_group(Fod_groupContext ctx) {
      String name = ctx.name.getText();
      _currentDhcpRelayGroup = _currentRoutingInstance.getDhcpRelayGroups()
            .computeIfAbsent(name, k -> new DhcpRelayGroup(k));
   }

   @Override
   public void exitFodg_interface(Fodg_interfaceContext ctx) {
      if (ctx.ALL() != null) {
         _currentDhcpRelayGroup.setAllInterfaces(true);
      }
      else {
         Interface iface = initInterface(ctx.interface_id());
         String interfaceName = iface.getName();
         _currentDhcpRelayGroup.getInterfaces().add(interfaceName);
      }
   }

   @Override
   public void enterFod_server_group(Fod_server_groupContext ctx) {
      String name = ctx.name.getText();
      final int line = ctx.name.getStart().getLine();
      DhcpRelayServerGroup serverGroup = _currentRoutingInstance
            .getDhcpRelayServerGroups()
            .computeIfAbsent(name, k -> new DhcpRelayServerGroup(k, line));
      Ip ip = new Ip(ctx.address.getText());
      serverGroup.getServers().add(ip);
   }

   @Override
   public void exitFod_group(Fod_groupContext ctx) {
      _currentDhcpRelayGroup = null;
   }

   @Override
   public void exitFo_dhcp_relay(Fo_dhcp_relayContext ctx) {
      _currentDhcpRelayGroup = null;
   }

   public static NamedPort getNamedPort(PortContext ctx) {
      if (ctx.AFS() != null) {
         return NamedPort.AFS;
      }
      else if (ctx.BGP() != null) {
         return NamedPort.BGP;
      }
      else if (ctx.BIFF() != null) {
         return NamedPort.BIFFudp_OR_EXECtcp;
      }
      else if (ctx.BOOTPC() != null) {
         return NamedPort.BOOTPC;
      }
      else if (ctx.BOOTPS() != null) {
         return NamedPort.BOOTPS_OR_DHCP;
      }
      else if (ctx.CMD() != null) {
         return NamedPort.CMDtcp_OR_SYSLOGudp;
      }
      else if (ctx.CVSPSERVER() != null) {
         return NamedPort.CVSPSERVER;
      }
      else if (ctx.DHCP() != null) {
         return NamedPort.BOOTPS_OR_DHCP;
      }
      else if (ctx.DOMAIN() != null) {
         return NamedPort.DOMAIN;
      }
      else if (ctx.EKLOGIN() != null) {
         return NamedPort.EKLOGIN;
      }
      else if (ctx.EKSHELL() != null) {
         return NamedPort.EKSHELL;
      }
      else if (ctx.EXEC() != null) {
         return NamedPort.BIFFudp_OR_EXECtcp;
      }
      else if (ctx.FINGER() != null) {
         return NamedPort.FINGER;
      }
      else if (ctx.FTP() != null) {
         return NamedPort.FTP;
      }
      else if (ctx.FTP_DATA() != null) {
         return NamedPort.FTP_DATA;
      }
      else if (ctx.HTTP() != null) {
         return NamedPort.HTTP;
      }
      else if (ctx.HTTPS() != null) {
         return NamedPort.HTTPS;
      }
      else if (ctx.IDENT() != null) {
         return NamedPort.IDENT;
      }
      else if (ctx.IMAP() != null) {
         return NamedPort.IMAP;
      }
      else if (ctx.KERBEROS_SEC() != null) {
         return NamedPort.KERBEROS_SEC;
      }
      else if (ctx.KLOGIN() != null) {
         return NamedPort.KLOGIN;
      }
      else if (ctx.KPASSWD() != null) {
         return NamedPort.KPASSWD;
      }
      else if (ctx.KRB_PROP() != null) {
         return NamedPort.KRB_PROP;
      }
      else if (ctx.KRBUPDATE() != null) {
         return NamedPort.KRBUPDATE;
      }
      else if (ctx.KSHELL() != null) {
         return NamedPort.KSHELL;
      }
      else if (ctx.LDAP() != null) {
         return NamedPort.LDAP;
      }
      else if (ctx.LDP() != null) {
         return NamedPort.LDP;
      }
      else if (ctx.LOGIN() != null) {
         return NamedPort.LOGINtcp_OR_WHOudp;
      }
      else if (ctx.MOBILEIP_AGENT() != null) {
         return NamedPort.MOBILE_IP_AGENT;
      }
      else if (ctx.MOBILIP_MN() != null) {
         return NamedPort.MOBILE_IP_MN;
      }
      else if (ctx.MSDP() != null) {
         return NamedPort.MSDP;
      }
      else if (ctx.NETBIOS_DGM() != null) {
         return NamedPort.NETBIOS_DGM;
      }
      else if (ctx.NETBIOS_NS() != null) {
         return NamedPort.NETBIOS_NS;
      }
      else if (ctx.NETBIOS_SSN() != null) {
         return NamedPort.NETBIOS_SSN;
      }
      else if (ctx.NFSD() != null) {
         return NamedPort.NFSD;
      }
      else if (ctx.NNTP() != null) {
         return NamedPort.NNTP;
      }
      else if (ctx.NTALK() != null) {
         return NamedPort.NTALK;
      }
      else if (ctx.NTP() != null) {
         return NamedPort.NTP;
      }
      else if (ctx.POP3() != null) {
         return NamedPort.POP3;
      }
      else if (ctx.PPTP() != null) {
         return NamedPort.PPTP;
      }
      else if (ctx.PRINTER() != null) {
         return NamedPort.LDP;
      }
      else if (ctx.RADACCT() != null) {
         return NamedPort.RADIUS_JUNIPER;
      }
      else if (ctx.RADIUS() != null) {
         return NamedPort.RADIUS_JUNIPER;
      }
      else if (ctx.RIP() != null) {
         return NamedPort.RIP;
      }
      else if (ctx.RKINIT() != null) {
         return NamedPort.RKINIT;
      }
      else if (ctx.SMTP() != null) {
         return NamedPort.SMTP;
      }
      else if (ctx.SNMP() != null) {
         return NamedPort.SNMP;
      }
      else if (ctx.SNMPTRAP() != null) {
         return NamedPort.SNMPTRAP;
      }
      else if (ctx.SNPP() != null) {
         return NamedPort.SNPP;
      }
      else if (ctx.SOCKS() != null) {
         return NamedPort.SOCKS;
      }
      else if (ctx.SSH() != null) {
         return NamedPort.SSH;
      }
      else if (ctx.SUNRPC() != null) {
         return NamedPort.SUNRPC;
      }
      else if (ctx.SYSLOG() != null) {
         return NamedPort.CMDtcp_OR_SYSLOGudp;
      }
      else if (ctx.TACACS() != null) {
         return NamedPort.TACACS;
      }
      else if (ctx.TACACS_DS() != null) {
         return NamedPort.TACACS_DS;
      }
      else if (ctx.TALK() != null) {
         return NamedPort.TALK;
      }
      else if (ctx.TELNET() != null) {
         return NamedPort.TELNET;
      }
      else if (ctx.TFTP() != null) {
         return NamedPort.TFTP;
      }
      else if (ctx.TIMED() != null) {
         return NamedPort.TIMED;
      }
      else if (ctx.WHO() != null) {
         return NamedPort.LOGINtcp_OR_WHOudp;
      }
      else if (ctx.XDMCP() != null) {
         return NamedPort.XDMCP;
      }
      else {
         throw new BatfishException("missing port-number mapping for port: \""
               + ctx.getText() + "\"");
      }
   }

   public static int getPortNumber(PortContext ctx) {
      if (ctx.DEC() != null) {
         int port = toInt(ctx.DEC());
         return port;
      }
      else {
         NamedPort namedPort = getNamedPort(ctx);
         return namedPort.number();
      }
   }

   private static Application toApplication(Junos_applicationContext ctx) {
      if (ctx.JUNOS_AOL() != null) {
         return JunosApplication.JUNOS_AOL;
      }
      else if (ctx.JUNOS_BGP() != null) {
         return JunosApplication.JUNOS_BGP;
      }
      else if (ctx.JUNOS_BIFF() != null) {
         return JunosApplication.JUNOS_BIFF;
      }
      else if (ctx.JUNOS_BOOTPC() != null) {
         return JunosApplication.JUNOS_BOOTPC;
      }
      else if (ctx.JUNOS_BOOTPS() != null) {
         return JunosApplication.JUNOS_BOOTPS;
      }
      else if (ctx.JUNOS_CHARGEN() != null) {
         return JunosApplication.JUNOS_CHARGEN;
      }
      else if (ctx.JUNOS_CIFS() != null) {
         return JunosApplication.JUNOS_CIFS;
      }
      else if (ctx.JUNOS_CVSPSERVER() != null) {
         return JunosApplication.JUNOS_CVSPSERVER;
      }
      else if (ctx.JUNOS_DHCP_CLIENT() != null) {
         return JunosApplication.JUNOS_DHCP_CLIENT;
      }
      else if (ctx.JUNOS_DHCP_RELAY() != null) {
         return JunosApplication.JUNOS_DHCP_RELAY;
      }
      else if (ctx.JUNOS_DHCP_SERVER() != null) {
         return JunosApplication.JUNOS_DHCP_SERVER;
      }
      else if (ctx.JUNOS_DISCARD() != null) {
         return JunosApplication.JUNOS_DISCARD;
      }
      else if (ctx.JUNOS_DNS_TCP() != null) {
         return JunosApplication.JUNOS_DNS_TCP;
      }
      else if (ctx.JUNOS_DNS_UDP() != null) {
         return JunosApplication.JUNOS_DNS_UDP;
      }
      else if (ctx.JUNOS_ECHO() != null) {
         return JunosApplication.JUNOS_ECHO;
      }
      else if (ctx.JUNOS_FINGER() != null) {
         return JunosApplication.JUNOS_FINGER;
      }
      else if (ctx.JUNOS_FTP() != null) {
         return JunosApplication.JUNOS_FTP;
      }
      else if (ctx.JUNOS_GNUTELLA() != null) {
         return JunosApplication.JUNOS_GNUTELLA;
      }
      else if (ctx.JUNOS_GOPHER() != null) {
         return JunosApplication.JUNOS_GOPHER;
      }
      else if (ctx.JUNOS_GRE() != null) {
         return JunosApplication.JUNOS_GRE;
      }
      else if (ctx.JUNOS_GTP() != null) {
         return JunosApplication.JUNOS_GTP;
      }
      else if (ctx.JUNOS_H323() != null) {
         return JunosApplication.JUNOS_H323;
      }
      else if (ctx.JUNOS_HTTP() != null) {
         return JunosApplication.JUNOS_HTTP;
      }
      else if (ctx.JUNOS_HTTP_EXT() != null) {
         return JunosApplication.JUNOS_HTTP_EXT;
      }
      else if (ctx.JUNOS_HTTPS() != null) {
         return JunosApplication.JUNOS_HTTPS;
      }
      else if (ctx.JUNOS_ICMP_ALL() != null) {
         return JunosApplication.JUNOS_ICMP_ALL;
      }
      else if (ctx.JUNOS_ICMP_PING() != null) {
         return JunosApplication.JUNOS_ICMP_PING;
      }
      else if (ctx.JUNOS_ICMP6_ALL() != null) {
         return JunosApplication.JUNOS_ICMP6_ALL;
      }
      else if (ctx.JUNOS_ICMP6_DST_UNREACH_ADDR() != null) {
         return JunosApplication.JUNOS_ICMP6_DST_UNREACH_ADDR;
      }
      else if (ctx.JUNOS_ICMP6_DST_UNREACH_ADMIN() != null) {
         return JunosApplication.JUNOS_ICMP6_DST_UNREACH_ADMIN;
      }
      else if (ctx.JUNOS_ICMP6_DST_UNREACH_BEYOND() != null) {
         return JunosApplication.JUNOS_ICMP6_DST_UNREACH_BEYOND;
      }
      else if (ctx.JUNOS_ICMP6_DST_UNREACH_PORT() != null) {
         return JunosApplication.JUNOS_ICMP6_DST_UNREACH_PORT;
      }
      else if (ctx.JUNOS_ICMP6_DST_UNREACH_ROUTE() != null) {
         return JunosApplication.JUNOS_ICMP6_DST_UNREACH_ROUTE;
      }
      else if (ctx.JUNOS_ICMP6_ECHO_REPLY() != null) {
         return JunosApplication.JUNOS_ICMP6_ECHO_REPLY;
      }
      else if (ctx.JUNOS_ICMP6_ECHO_REQUEST() != null) {
         return JunosApplication.JUNOS_ICMP6_ECHO_REQUEST;
      }
      else if (ctx.JUNOS_ICMP6_PACKET_TO_BIG() != null) {
         return JunosApplication.JUNOS_ICMP6_PACKET_TO_BIG;
      }
      else if (ctx.JUNOS_ICMP6_PARAM_PROB_HEADER() != null) {
         return JunosApplication.JUNOS_ICMP6_PARAM_PROB_HEADER;
      }
      else if (ctx.JUNOS_ICMP6_PARAM_PROB_NEXTHDR() != null) {
         return JunosApplication.JUNOS_ICMP6_PARAM_PROB_NEXTHDR;
      }
      else if (ctx.JUNOS_ICMP6_PARAM_PROB_OPTION() != null) {
         return JunosApplication.JUNOS_ICMP6_PARAM_PROB_OPTION;
      }
      else if (ctx.JUNOS_ICMP6_TIME_EXCEED_REASSEMBLY() != null) {
         return JunosApplication.JUNOS_ICMP6_TIME_EXCEED_REASSEMBLY;
      }
      else if (ctx.JUNOS_ICMP6_TIME_EXCEED_TRANSIT() != null) {
         return JunosApplication.JUNOS_ICMP6_TIME_EXCEED_TRANSIT;
      }
      else if (ctx.JUNOS_IDENT() != null) {
         return JunosApplication.JUNOS_IDENT;
      }
      else if (ctx.JUNOS_IKE() != null) {
         return JunosApplication.JUNOS_IKE;
      }
      else if (ctx.JUNOS_IKE_NAT() != null) {
         return JunosApplication.JUNOS_IKE_NAT;
      }
      else if (ctx.JUNOS_IMAP() != null) {
         return JunosApplication.JUNOS_IMAP;
      }
      else if (ctx.JUNOS_IMAPS() != null) {
         return JunosApplication.JUNOS_IMAPS;
      }
      else if (ctx.JUNOS_INTERNET_LOCATOR_SERVICE() != null) {
         return JunosApplication.JUNOS_INTERNET_LOCATOR_SERVICE;
      }
      else if (ctx.JUNOS_IRC() != null) {
         return JunosApplication.JUNOS_IRC;
      }
      else if (ctx.JUNOS_L2TP() != null) {
         return JunosApplication.JUNOS_L2TP;
      }
      else if (ctx.JUNOS_LDAP() != null) {
         return JunosApplication.JUNOS_LDAP;
      }
      else if (ctx.JUNOS_LDP_TCP() != null) {
         return JunosApplication.JUNOS_LDP_TCP;
      }
      else if (ctx.JUNOS_LDP_UDP() != null) {
         return JunosApplication.JUNOS_LDP_UDP;
      }
      else if (ctx.JUNOS_LPR() != null) {
         return JunosApplication.JUNOS_LPR;
      }
      else if (ctx.JUNOS_MAIL() != null) {
         return JunosApplication.JUNOS_MAIL;
      }
      else if (ctx.JUNOS_MGCP() != null) {
         return JunosApplication.JUNOS_MGCP;
      }
      else if (ctx.JUNOS_MGCP_CA() != null) {
         return JunosApplication.JUNOS_MGCP_CA;
      }
      else if (ctx.JUNOS_MGCP_UA() != null) {
         return JunosApplication.JUNOS_MGCP_UA;
      }
      else if (ctx.JUNOS_MS_RPC() != null) {
         return JunosApplication.JUNOS_MS_RPC;
      }
      else if (ctx.JUNOS_MS_RPC_ANY() != null) {
         return JunosApplication.JUNOS_MS_RPC_ANY;
      }
      else if (ctx.JUNOS_MS_RPC_EPM() != null) {
         return JunosApplication.JUNOS_MS_RPC_EPM;
      }
      else if (ctx.JUNOS_MS_RPC_IIS_COM() != null) {
         return JunosApplication.JUNOS_MS_RPC_IIS_COM;
      }
      else if (ctx.JUNOS_MS_RPC_IIS_COM_1() != null) {
         return JunosApplication.JUNOS_MS_RPC_IIS_COM_1;
      }
      else if (ctx.JUNOS_MS_RPC_IIS_COM_ADMINBASE() != null) {
         return JunosApplication.JUNOS_MS_RPC_IIS_COM_ADMINBASE;
      }
      else if (ctx.JUNOS_MS_RPC_MSEXCHANGE() != null) {
         return JunosApplication.JUNOS_MS_RPC_MSEXCHANGE;
      }
      else if (ctx.JUNOS_MS_RPC_MSEXCHANGE_DIRECTORY_NSP() != null) {
         return JunosApplication.JUNOS_MS_RPC_MSEXCHANGE_DIRECTORY_NSP;
      }
      else if (ctx.JUNOS_MS_RPC_MSEXCHANGE_DIRECTORY_RFR() != null) {
         return JunosApplication.JUNOS_MS_RPC_MSEXCHANGE_DIRECTORY_RFR;
      }
      else if (ctx.JUNOS_MS_RPC_MSEXCHANGE_INFO_STORE() != null) {
         return JunosApplication.JUNOS_MS_RPC_MSEXCHANGE_INFO_STORE;
      }
      else if (ctx.JUNOS_MS_RPC_TCP() != null) {
         return JunosApplication.JUNOS_MS_RPC_TCP;
      }
      else if (ctx.JUNOS_MS_RPC_UDP() != null) {
         return JunosApplication.JUNOS_MS_RPC_UDP;
      }
      else if (ctx.JUNOS_MS_RPC_UUID_ANY_TCP() != null) {
         return JunosApplication.JUNOS_MS_RPC_UUID_ANY_TCP;
      }
      else if (ctx.JUNOS_MS_RPC_UUID_ANY_UDP() != null) {
         return JunosApplication.JUNOS_MS_RPC_UUID_ANY_UDP;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_ADMIN() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_ADMIN;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_ADMIN2() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_ADMIN2;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_MGMT() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_MGMT;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_CALLRESULT() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_CALLRESULT;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_CLASSOBJECT() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_CLASSOBJECT;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_LEVEL1LOGIN() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_LEVEL1LOGIN;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_LOGIN_CLIENTID() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_LOGIN_CLIENTID;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_LOGIN_HELPER() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_LOGIN_HELPER;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_OBJECTSINK() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_OBJECTSINK;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_REFRESHING_SERVICES() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_REFRESHING_SERVICES;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_REMOTE_REFRESHER() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_REMOTE_REFRESHER;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_SERVICES() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_SERVICES;
      }
      else if (ctx.JUNOS_MS_RPC_WMIC_WEBM_SHUTDOWN() != null) {
         return JunosApplication.JUNOS_MS_RPC_WMIC_WEBM_SHUTDOWN;
      }
      else if (ctx.JUNOS_MS_SQL() != null) {
         return JunosApplication.JUNOS_MS_SQL;
      }
      else if (ctx.JUNOS_MSN() != null) {
         return JunosApplication.JUNOS_MSN;
      }
      else if (ctx.JUNOS_NBDS() != null) {
         return JunosApplication.JUNOS_NBDS;
      }
      else if (ctx.JUNOS_NBNAME() != null) {
         return JunosApplication.JUNOS_NBNAME;
      }
      else if (ctx.JUNOS_NETBIOS_SESSION() != null) {
         return JunosApplication.JUNOS_NETBIOS_SESSION;
      }
      else if (ctx.JUNOS_NFS() != null) {
         return JunosApplication.JUNOS_NFS;
      }
      else if (ctx.JUNOS_NFSD_TCP() != null) {
         return JunosApplication.JUNOS_NFSD_TCP;
      }
      else if (ctx.JUNOS_NFSD_UDP() != null) {
         return JunosApplication.JUNOS_NFSD_UDP;
      }
      else if (ctx.JUNOS_NNTP() != null) {
         return JunosApplication.JUNOS_NNTP;
      }
      else if (ctx.JUNOS_NS_GLOBAL() != null) {
         return JunosApplication.JUNOS_NS_GLOBAL;
      }
      else if (ctx.JUNOS_NS_GLOBAL_PRO() != null) {
         return JunosApplication.JUNOS_NS_GLOBAL_PRO;
      }
      else if (ctx.JUNOS_NSM() != null) {
         return JunosApplication.JUNOS_NSM;
      }
      else if (ctx.JUNOS_NTALK() != null) {
         return JunosApplication.JUNOS_NTALK;
      }
      else if (ctx.JUNOS_NTP() != null) {
         return JunosApplication.JUNOS_NTP;
      }
      else if (ctx.JUNOS_OSPF() != null) {
         return JunosApplication.JUNOS_OSPF;
      }
      else if (ctx.JUNOS_PC_ANYWHERE() != null) {
         return JunosApplication.JUNOS_PC_ANYWHERE;
      }
      else if (ctx.JUNOS_PERSISTENT_NAT() != null) {
         return JunosApplication.JUNOS_PERSISTENT_NAT;
      }
      else if (ctx.JUNOS_PING() != null) {
         return JunosApplication.JUNOS_PING;
      }
      else if (ctx.JUNOS_PINGV6() != null) {
         return JunosApplication.JUNOS_PINGV6;
      }
      else if (ctx.JUNOS_POP3() != null) {
         return JunosApplication.JUNOS_POP3;
      }
      else if (ctx.JUNOS_PPTP() != null) {
         return JunosApplication.JUNOS_PPTP;
      }
      else if (ctx.JUNOS_PRINTER() != null) {
         return JunosApplication.JUNOS_PRINTER;
      }
      else if (ctx.JUNOS_R2CP() != null) {
         return JunosApplication.JUNOS_R2CP;
      }
      else if (ctx.JUNOS_RADACCT() != null) {
         return JunosApplication.JUNOS_RADACCT;
      }
      else if (ctx.JUNOS_RADIUS() != null) {
         return JunosApplication.JUNOS_RADIUS;
      }
      else if (ctx.JUNOS_REALAUDIO() != null) {
         return JunosApplication.JUNOS_REALAUDIO;
      }
      else if (ctx.JUNOS_RIP() != null) {
         return JunosApplication.JUNOS_RIP;
      }
      else if (ctx.JUNOS_ROUTING_INBOUND() != null) {
         return JunosApplication.JUNOS_ROUTING_INBOUND;
      }
      else if (ctx.JUNOS_RSH() != null) {
         return JunosApplication.JUNOS_RSH;
      }
      else if (ctx.JUNOS_RTSP() != null) {
         return JunosApplication.JUNOS_RTSP;
      }
      else if (ctx.JUNOS_SCCP() != null) {
         return JunosApplication.JUNOS_SCCP;
      }
      else if (ctx.JUNOS_SCTP_ANY() != null) {
         return JunosApplication.JUNOS_SCTP_ANY;
      }
      else if (ctx.JUNOS_SIP() != null) {
         return JunosApplication.JUNOS_SIP;
      }
      else if (ctx.JUNOS_SMB() != null) {
         return JunosApplication.JUNOS_SMB;
      }
      else if (ctx.JUNOS_SMB_SESSION() != null) {
         return JunosApplication.JUNOS_SMB_SESSION;
      }
      else if (ctx.JUNOS_SMTP() != null) {
         return JunosApplication.JUNOS_SMTP;
      }
      else if (ctx.JUNOS_SNMP_AGENTX() != null) {
         return JunosApplication.JUNOS_SNMP_AGENTX;
      }
      else if (ctx.JUNOS_SNPP() != null) {
         return JunosApplication.JUNOS_SNPP;
      }
      else if (ctx.JUNOS_SQL_MONITOR() != null) {
         return JunosApplication.JUNOS_SQL_MONITOR;
      }
      else if (ctx.JUNOS_SQLNET_V1() != null) {
         return JunosApplication.JUNOS_SQLNET_V1;
      }
      else if (ctx.JUNOS_SQLNET_V2() != null) {
         return JunosApplication.JUNOS_SQLNET_V2;
      }
      else if (ctx.JUNOS_SSH() != null) {
         return JunosApplication.JUNOS_SSH;
      }
      else if (ctx.JUNOS_STUN() != null) {
         return JunosApplication.JUNOS_STUN;
      }
      else if (ctx.JUNOS_SUN_RPC() != null) {
         return JunosApplication.JUNOS_SUN_RPC;
      }
      else if (ctx.JUNOS_SUN_RPC_ANY() != null) {
         return JunosApplication.JUNOS_SUN_RPC_ANY;
      }
      else if (ctx.JUNOS_SUN_RPC_ANY_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_ANY_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_ANY_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_ANY_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_MOUNTD() != null) {
         return JunosApplication.JUNOS_SUN_RPC_MOUNTD;
      }
      else if (ctx.JUNOS_SUN_RPC_MOUNTD_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_MOUNTD_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_MOUNTD_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_MOUNTD_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_NFS() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NFS;
      }
      else if (ctx.JUNOS_SUN_RPC_NFS_ACCESS() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NFS_ACCESS;
      }
      else if (ctx.JUNOS_SUN_RPC_NFS_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NFS_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_NFS_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NFS_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_NLOCKMGR() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NLOCKMGR;
      }
      else if (ctx.JUNOS_SUN_RPC_NLOCKMGR_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NLOCKMGR_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_NLOCKMGR_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_NLOCKMGR_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_PORTMAP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_PORTMAP;
      }
      else if (ctx.JUNOS_SUN_RPC_PORTMAP_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_PORTMAP_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_PORTMAP_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_PORTMAP_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_RQUOTAD() != null) {
         return JunosApplication.JUNOS_SUN_RPC_RQUOTAD;
      }
      else if (ctx.JUNOS_SUN_RPC_RQUOTAD_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_RQUOTAD_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_RQUOTAD_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_RQUOTAD_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_RUSERD() != null) {
         return JunosApplication.JUNOS_SUN_RPC_RUSERD;
      }
      else if (ctx.JUNOS_SUN_RPC_RUSERD_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_RUSERD_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_RUSERD_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_RUSERD_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_SADMIND() != null) {
         return JunosApplication.JUNOS_SUN_RPC_SADMIND;
      }
      else if (ctx.JUNOS_SUN_RPC_SADMIND_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_SADMIND_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_SADMIND_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_SADMIND_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_SPRAYD() != null) {
         return JunosApplication.JUNOS_SUN_RPC_SPRAYD;
      }
      else if (ctx.JUNOS_SUN_RPC_SPRAYD_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_SPRAYD_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_SPRAYD_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_SPRAYD_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_STATUS() != null) {
         return JunosApplication.JUNOS_SUN_RPC_STATUS;
      }
      else if (ctx.JUNOS_SUN_RPC_STATUS_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_STATUS_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_STATUS_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_STATUS_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_WALLD() != null) {
         return JunosApplication.JUNOS_SUN_RPC_WALLD;
      }
      else if (ctx.JUNOS_SUN_RPC_WALLD_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_WALLD_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_WALLD_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_WALLD_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_YPBIND() != null) {
         return JunosApplication.JUNOS_SUN_RPC_YPBIND;
      }
      else if (ctx.JUNOS_SUN_RPC_YPBIND_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_YPBIND_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_YPBIND_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_YPBIND_UDP;
      }
      else if (ctx.JUNOS_SUN_RPC_YPSERV() != null) {
         return JunosApplication.JUNOS_SUN_RPC_YPSERV;
      }
      else if (ctx.JUNOS_SUN_RPC_YPSERV_TCP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_YPSERV_TCP;
      }
      else if (ctx.JUNOS_SUN_RPC_YPSERV_UDP() != null) {
         return JunosApplication.JUNOS_SUN_RPC_YPSERV_UDP;
      }
      else if (ctx.JUNOS_SYSLOG() != null) {
         return JunosApplication.JUNOS_SYSLOG;
      }
      else if (ctx.JUNOS_TACACS() != null) {
         return JunosApplication.JUNOS_TACACS;
      }
      else if (ctx.JUNOS_TACACS_DS() != null) {
         return JunosApplication.JUNOS_TACACS_DS;
      }
      else if (ctx.JUNOS_TALK() != null) {
         return JunosApplication.JUNOS_TALK;
      }
      else if (ctx.JUNOS_TCP_ANY() != null) {
         return JunosApplication.JUNOS_TCP_ANY;
      }
      else if (ctx.JUNOS_TELNET() != null) {
         return JunosApplication.JUNOS_TELNET;
      }
      else if (ctx.JUNOS_TFTP() != null) {
         return JunosApplication.JUNOS_TFTP;
      }
      else if (ctx.JUNOS_UDP_ANY() != null) {
         return JunosApplication.JUNOS_UDP_ANY;
      }
      else if (ctx.JUNOS_UUCP() != null) {
         return JunosApplication.JUNOS_UUCP;
      }
      else if (ctx.JUNOS_VDO_LIVE() != null) {
         return JunosApplication.JUNOS_VDO_LIVE;
      }
      else if (ctx.JUNOS_VNC() != null) {
         return JunosApplication.JUNOS_VNC;
      }
      else if (ctx.JUNOS_WAIS() != null) {
         return JunosApplication.JUNOS_WAIS;
      }
      else if (ctx.JUNOS_WHO() != null) {
         return JunosApplication.JUNOS_WHO;
      }
      else if (ctx.JUNOS_WHOIS() != null) {
         return JunosApplication.JUNOS_WHOIS;
      }
      else if (ctx.JUNOS_WINFRAME() != null) {
         return JunosApplication.JUNOS_WINFRAME;
      }
      else if (ctx.JUNOS_WXCONTROL() != null) {
         return JunosApplication.JUNOS_WXCONTROL;
      }
      else if (ctx.JUNOS_X_WINDOWS() != null) {
         return JunosApplication.JUNOS_X_WINDOWS;
      }
      else if (ctx.JUNOS_XNM_CLEAR_TEXT() != null) {
         return JunosApplication.JUNOS_XNM_CLEAR_TEXT;
      }
      else if (ctx.JUNOS_XNM_SSL() != null) {
         return JunosApplication.JUNOS_XNM_SSL;
      }
      else if (ctx.JUNOS_YMSG() != null) {
         return JunosApplication.JUNOS_YMSG;
      }
      else {
         throw new BatfishException(
               "missing application mapping for application: \"" + ctx.getText()
                     + "\"");
      }
   }

   private static long toCommunityLong(Sc_literalContext sc_literal) {
      String text = sc_literal.COMMUNITY_LITERAL().getText();
      return CommonUtil.communityStringToLong(text);
   }

   private static long toCommunityLong(Sc_namedContext ctx) {
      if (ctx.NO_ADVERTISE() != null) {
         return 0xFFFFFF02l;
      }
      if (ctx.NO_EXPORT() != null) {
         return 0xFFFFFF01l;
      }
      else {
         throw new BatfishException(
               "missing named-community-to-long mapping for: \"" + ctx.getText()
                     + "\"");
      }
   }

   private static long toCommunityLong(Standard_communityContext ctx) {
      if (ctx.sc_literal() != null) {
         return toCommunityLong(ctx.sc_literal());
      }
      else if (ctx.sc_named() != null) {
         return toCommunityLong(ctx.sc_named());
      }
      else {
         throw new BatfishException("Cannot convert to community long");
      }
   }

   private static EncryptionAlgorithm toEncryptionAlgorithm(
         Encryption_algorithmContext ctx) {
      if (ctx.THREEDES_CBC() != null) {
         return EncryptionAlgorithm.THREEDES_CBC;
      }
      else if (ctx.AES_128_CBC() != null) {
         return EncryptionAlgorithm.AES_128_CBC;
      }
      else if (ctx.AES_192_CBC() != null) {
         return EncryptionAlgorithm.AES_192_CBC;
      }
      else if (ctx.AES_256_CBC() != null) {
         return EncryptionAlgorithm.AES_256_CBC;
      }
      else if (ctx.DES_CBC() != null) {
         return EncryptionAlgorithm.DES_CBC;
      }
      else {
         throw new BatfishException(
               "Invalid encryption algorithm: " + ctx.getText());
      }
   }

   private static HostProtocol toHostProtocol(Hib_protocolContext ctx) {
      if (ctx.ALL() != null) {
         return HostProtocol.ALL;
      }
      else if (ctx.BFD() != null) {
         return HostProtocol.BFD;
      }
      else if (ctx.BGP() != null) {
         return HostProtocol.BGP;
      }
      else if (ctx.DVMRP() != null) {
         return HostProtocol.DVMRP;
      }
      else if (ctx.IGMP() != null) {
         return HostProtocol.IGMP;
      }
      else if (ctx.LDP() != null) {
         return HostProtocol.LDP;
      }
      else if (ctx.MSDP() != null) {
         return HostProtocol.MSDP;
      }
      else if (ctx.NHRP() != null) {
         return HostProtocol.NHRP;
      }
      else if (ctx.OSPF() != null) {
         return HostProtocol.OSPF;
      }
      else if (ctx.OSPF3() != null) {
         return HostProtocol.OSPF3;
      }
      else if (ctx.PGM() != null) {
         return HostProtocol.PGM;
      }
      else if (ctx.PIM() != null) {
         return HostProtocol.PIM;
      }
      else if (ctx.RIP() != null) {
         return HostProtocol.RIP;
      }
      else if (ctx.RIPNG() != null) {
         return HostProtocol.RIPNG;
      }
      else if (ctx.ROUTER_DISCOVERY() != null) {
         return HostProtocol.ROUTER_DISCOVERY;
      }
      else if (ctx.RSVP() != null) {
         return HostProtocol.RSVP;
      }
      else if (ctx.SAP() != null) {
         return HostProtocol.SAP;
      }
      else if (ctx.VRRP() != null) {
         return HostProtocol.VRRP;
      }
      else {
         throw new BatfishException("Invalid host protocol");
      }
   }

   private static HostSystemService toHostSystemService(
         Hib_system_serviceContext ctx) {
      if (ctx.ALL() != null) {
         return HostSystemService.ALL;
      }
      else if (ctx.ANY_SERVICE() != null) {
         return HostSystemService.ANY_SERVICE;
      }
      else if (ctx.DHCP() != null) {
         return HostSystemService.DHCP;
      }
      else if (ctx.DNS() != null) {
         return HostSystemService.DNS;
      }
      else if (ctx.FINGER() != null) {
         return HostSystemService.FINGER;
      }
      else if (ctx.FTP() != null) {
         return HostSystemService.FTP;
      }
      else if (ctx.HTTP() != null) {
         return HostSystemService.HTTP;
      }
      else if (ctx.HTTPS() != null) {
         return HostSystemService.HTTPS;
      }
      else if (ctx.IDENT_RESET() != null) {
         return HostSystemService.IDENT_RESET;
      }
      else if (ctx.IKE() != null) {
         return HostSystemService.IKE;
      }
      else if (ctx.LSPING() != null) {
         return HostSystemService.LSPING;
      }
      else if (ctx.NETCONF() != null) {
         return HostSystemService.NETCONF;
      }
      else if (ctx.NTP() != null) {
         return HostSystemService.NTP;
      }
      else if (ctx.PING() != null) {
         return HostSystemService.PING;
      }
      else if (ctx.R2CP() != null) {
         return HostSystemService.R2CP;
      }
      else if (ctx.REVERSE_SSH() != null) {
         return HostSystemService.REVERSE_SSH;
      }
      else if (ctx.REVERSE_TELNET() != null) {
         return HostSystemService.REVERSE_TELNET;
      }
      else if (ctx.RLOGIN() != null) {
         return HostSystemService.RLOGIN;
      }
      else if (ctx.RPM() != null) {
         return HostSystemService.RPM;
      }
      else if (ctx.RSH() != null) {
         return HostSystemService.RSH;
      }
      else if (ctx.SIP() != null) {
         return HostSystemService.SIP;
      }
      else if (ctx.SNMP() != null) {
         return HostSystemService.SNMP;
      }
      else if (ctx.SNMP_TRAP() != null) {
         return HostSystemService.SNMP_TRAP;
      }
      else if (ctx.SSH() != null) {
         return HostSystemService.SSH;
      }
      else if (ctx.TELNET() != null) {
         return HostSystemService.TELNET;
      }
      else if (ctx.TFTP() != null) {
         return HostSystemService.TFTP;
      }
      else if (ctx.TRACEROUTE() != null) {
         return HostSystemService.TRACEROUTE;
      }
      else if (ctx.XNM_CLEAR_TEXT() != null) {
         return HostSystemService.XNM_CLEAR_TEXT;
      }
      else if (ctx.XNM_SSL() != null) {
         return HostSystemService.XNM_SSL;
      }
      else {
         throw new BatfishException("Invalid host system service");
      }

   }

   private static int toIcmpCode(Icmp_codeContext ctx) {
      if (ctx.FRAGMENTATION_NEEDED() != null) {
         return IcmpCode.PACKET_TOO_BIG;
      }
      else if (ctx.HOST_UNREACHABLE() != null) {
         return IcmpCode.DESTINATION_HOST_UNREACHABLE;
      }
      else {
         throw new BatfishException(
               "Missing mapping for icmp-code: '" + ctx.getText() + "'");
      }
   }

   private static int toIcmpType(Icmp_typeContext ctx) {
      if (ctx.ECHO_REPLY() != null) {
         return IcmpType.ECHO_REPLY;
      }
      else if (ctx.ECHO_REQUEST() != null) {
         return IcmpType.ECHO_REQUEST;
      }
      else if (ctx.PARAMETER_PROBLEM() != null) {
         return IcmpType.PARAMETER_PROBLEM;
      }
      else if (ctx.SOURCE_QUENCH() != null) {
         return IcmpType.SOURCE_QUENCH;
      }
      else if (ctx.TIME_EXCEEDED() != null) {
         return IcmpType.TIME_EXCEEDED;
      }
      else if (ctx.UNREACHABLE() != null) {
         return IcmpType.DESTINATION_UNREACHABLE;
      }
      else {
         throw new BatfishException(
               "Missing mapping for icmp-type: '" + ctx.getText() + "'");
      }
   }

   private static IkeAuthenticationAlgorithm toIkeAuthenticationAlgorithm(
         Ike_authentication_algorithmContext ctx) {
      if (ctx.MD5() != null) {
         return IkeAuthenticationAlgorithm.MD5;
      }
      else if (ctx.SHA1() != null) {
         return IkeAuthenticationAlgorithm.SHA1;
      }
      else if (ctx.SHA_256() != null) {
         return IkeAuthenticationAlgorithm.SHA_256;
      }
      else if (ctx.SHA_384() != null) {
         return IkeAuthenticationAlgorithm.SHA_384;
      }
      else {
         throw new BatfishException(
               "invalid ike authentication algorithm: " + ctx.getText());
      }
   }

   private static IkeAuthenticationMethod toIkeAuthenticationMethod(
         Ike_authentication_methodContext ctx) {
      if (ctx.DSA_SIGNATURES() != null) {
         return IkeAuthenticationMethod.DSA_SIGNATURES;
      }
      else if (ctx.PRE_SHARED_KEYS() != null) {
         return IkeAuthenticationMethod.PRE_SHARED_KEYS;
      }
      else if (ctx.RSA_SIGNATURES() != null) {
         return IkeAuthenticationMethod.RSA_SIGNATURES;
      }
      else {
         throw new BatfishException(
               "Invalid ike authentication method: " + ctx.getText());
      }
   }

   private static Integer toInt(TerminalNode node) {
      return toInt(node.getSymbol());
   }

   private static int toInt(Token token) {
      return Integer.parseInt(token.getText());
   }

   private static IpProtocol toIpProtocol(Ip_protocolContext ctx) {
      if (ctx.DEC() != null) {
         int protocolNum = toInt(ctx.DEC());
         return IpProtocol.fromNumber(protocolNum);
      }
      else if (ctx.AH() != null) {
         return IpProtocol.AHP;
      }
      else if (ctx.DSTOPTS() != null) {
         return IpProtocol.IPV6_OPTS;
      }
      else if (ctx.EGP() != null) {
         return IpProtocol.EGP;
      }
      else if (ctx.ESP() != null) {
         return IpProtocol.ESP;
      }
      else if (ctx.FRAGMENT() != null) {
         return IpProtocol.IPV6_FRAG;
      }
      else if (ctx.GRE() != null) {
         return IpProtocol.GRE;
      }
      else if (ctx.HOP_BY_HOP() != null) {
         return IpProtocol.HOPOPT;
      }
      else if (ctx.ICMP() != null) {
         return IpProtocol.ICMP;
      }
      else if (ctx.ICMP6() != null) {
         return IpProtocol.IPV6_ICMP;
      }
      else if (ctx.ICMPV6() != null) {
         return IpProtocol.IPV6_ICMP;
      }
      else if (ctx.IGMP() != null) {
         return IpProtocol.IGMP;
      }
      else if (ctx.IPIP() != null) {
         return IpProtocol.IPINIP;
      }
      else if (ctx.IPV6() != null) {
         return IpProtocol.IPV6;
      }
      else if (ctx.OSPF() != null) {
         return IpProtocol.OSPF;
      }
      else if (ctx.PIM() != null) {
         return IpProtocol.PIM;
      }
      else if (ctx.RSVP() != null) {
         return IpProtocol.RSVP;
      }
      else if (ctx.SCTP() != null) {
         return IpProtocol.SCTP;
      }
      else if (ctx.TCP() != null) {
         return IpProtocol.TCP;
      }
      else if (ctx.UDP() != null) {
         return IpProtocol.UDP;
      }
      else if (ctx.VRRP() != null) {
         return IpProtocol.VRRP;
      }
      else {
         throw new BatfishException(
               "missing protocol-enum mapping for protocol: \"" + ctx.getText()
                     + "\"");
      }
   }

   private static IpsecAuthenticationAlgorithm toIpsecAuthenticationAlgorithm(
         Ipsec_authentication_algorithmContext ctx) {
      if (ctx.HMAC_MD5_96() != null) {
         return IpsecAuthenticationAlgorithm.HMAC_MD5_96;
      }
      else if (ctx.HMAC_SHA1_96() != null) {
         return IpsecAuthenticationAlgorithm.HMAC_SHA1_96;
      }
      else {
         throw new BatfishException(
               "invalid ipsec authentication algorithm: " + ctx.getText());
      }
   }

   private static IpsecProtocol toIpsecProtocol(Ipsec_protocolContext ctx) {
      if (ctx.AH() != null) {
         return IpsecProtocol.AH;
      }
      else if (ctx.ESP() != null) {
         return IpsecProtocol.ESP;
      }
      else {
         throw new BatfishException("invalid ipsec protocol: " + ctx.getText());
      }
   }

   private static List<SubRange> toRange(RangeContext ctx) {
      List<SubRange> range = new ArrayList<>();
      for (SubrangeContext sc : ctx.range_list) {
         SubRange sr = toSubRange(sc);
         range.add(sr);
      }
      return range;
   }

   private static RoutingProtocol toRoutingProtocol(
         Routing_protocolContext ctx) {
      if (ctx.AGGREGATE() != null) {
         return RoutingProtocol.AGGREGATE;
      }
      else if (ctx.BGP() != null) {
         return RoutingProtocol.BGP;
      }
      else if (ctx.DIRECT() != null) {
         return RoutingProtocol.CONNECTED;
      }
      else if (ctx.ISIS() != null) {
         return RoutingProtocol.ISIS;
      }
      else if (ctx.LDP() != null) {
         return RoutingProtocol.LDP;
      }
      else if (ctx.LOCAL() != null) {
         return RoutingProtocol.LOCAL;
      }
      else if (ctx.OSPF() != null) {
         return RoutingProtocol.OSPF;
      }
      else if (ctx.OSPF3() != null) {
         return RoutingProtocol.OSPF3;
      }
      else if (ctx.RSVP() != null) {
         return RoutingProtocol.RSVP;
      }
      else if (ctx.STATIC() != null) {
         return RoutingProtocol.STATIC;
      }
      else {
         throw new BatfishException("missing routing protocol-enum mapping");
      }
   }

   private static SubRange toSubRange(SubrangeContext ctx) {
      int low = toInt(ctx.low);
      int high = (ctx.high != null) ? toInt(ctx.high) : low;
      return new SubRange(low, high);
   }

   private static TcpFlags toTcpFlags(Tcp_flags_alternativeContext ctx) {
      TcpFlags tcpFlags = new TcpFlags();
      for (Tcp_flags_literalContext literalCtx : ctx.literals) {
         boolean value = literalCtx.BANG() == null;
         Tcp_flags_atomContext atom = literalCtx.tcp_flags_atom();
         if (atom.ACK() != null) {
            tcpFlags.setUseAck(true);
            tcpFlags.setAck(value);
         }
         else if (atom.CWR() != null) {
            tcpFlags.setUseCwr(true);
            tcpFlags.setCwr(value);
         }
         else if (atom.ECE() != null) {
            tcpFlags.setUseEce(true);
            tcpFlags.setEce(value);
         }
         else if (atom.FIN() != null) {
            tcpFlags.setUseFin(true);
            tcpFlags.setFin(value);
         }
         else if (atom.PSH() != null) {
            tcpFlags.setUsePsh(true);
            tcpFlags.setPsh(value);
         }
         else if (atom.RST() != null) {
            tcpFlags.setUseRst(true);
            tcpFlags.setRst(value);
         }
         else if (atom.SYN() != null) {
            tcpFlags.setUseSyn(true);
            tcpFlags.setSyn(value);
         }
         else if (atom.URG() != null) {
            tcpFlags.setUseUrg(true);
            tcpFlags.setUrg(value);
         }
         else {
            throw new BatfishException(
                  "Invalid tcp-flags atom: " + atom.getText());
         }
      }
      return tcpFlags;
   }

   private static List<TcpFlags> toTcpFlags(Tcp_flagsContext ctx) {
      List<TcpFlags> tcpFlagsList = new ArrayList<>();
      for (Tcp_flags_alternativeContext alternativeCtx : ctx.alternatives) {
         TcpFlags tcpFlags = toTcpFlags(alternativeCtx);
         tcpFlagsList.add(tcpFlags);
      }
      return tcpFlagsList;
   }

   private static String unquote(String text) {
      if (text.length() == 0) {
         return text;
      }
      if (text.charAt(0) != '"') {
         return text;
      }
      else if (text.charAt(text.length() - 1) != '"') {
         throw new BatfishException("Improperly-quoted string");
      }
      else {
         return text.substring(1, text.length() - 1);
      }
   }

   private JuniperConfiguration _configuration;

   private int _conjunctionPolicyIndex;

   private AddressBook _currentAddressBook;

   private AddressSetAddressBookEntry _currentAddressSetAddressBookEntry;

   private AggregateRoute _currentAggregateRoute;

   private BaseApplication _currentApplication;

   private Term _currentApplicationTerm;

   private OspfArea _currentArea;

   private BgpGroup _currentBgpGroup;

   private CommunityList _currentCommunityList;

   private FirewallFilter _currentFilter;

   private Family _currentFirewallFamily;

   private Zone _currentFromZone;

   private FwTerm _currentFwTerm;

   private GeneratedRoute _currentGeneratedRoute;

   private IkeGateway _currentIkeGateway;

   private IkePolicy _currentIkePolicy;

   private IkeProposal _currentIkeProposal;

   private Interface _currentInterface;

   private Prefix _currentInterfacePrefix;

   private IpsecPolicy _currentIpsecPolicy;

   private IpsecProposal _currentIpsecProposal;

   private IpsecVpn _currentIpsecVpn;

   private Interface _currentIsisInterface;

   private IsisInterfaceLevelSettings _currentIsisInterfaceLevelSettings;

   private IsisLevelSettings _currentIsisLevelSettings;

   private Interface _currentMasterInterface;

   private Interface _currentOspfInterface;

   private PolicyStatement _currentPolicyStatement;

   private PrefixList _currentPrefixList;

   private PsTerm _currentPsTerm;

   private Set<PsThen> _currentPsThens;

   private RoutingInformationBase _currentRib;

   private Route6FilterLine _currentRoute6FilterLine;

   private Prefix6 _currentRoute6FilterPrefix;

   private RouteFilter _currentRouteFilter;

   private Route4FilterLine _currentRouteFilterLine;

   private Prefix _currentRouteFilterPrefix;

   private RoutingInstance _currentRoutingInstance;

   private SnmpCommunity _currentSnmpCommunity;

   private SnmpServer _currentSnmpServer;

   private StaticRoute _currentStaticRoute;

   private Zone _currentToZone;

   private VrrpGroup _currentVrrpGroup;

   private Zone _currentZone;

   private FirewallFilter _currentZoneInboundFilter;

   private Interface _currentZoneInterface;

   private LineAction _defaultCrossZoneAction;

   private int _disjunctionPolicyIndex;

   private boolean _hasZones;

   private FlatJuniperCombinedParser _parser;

   private final Map<PsTerm, RouteFilter> _termRouteFilters;

   private final String _text;

   private final Set<String> _unimplementedFeatures;

   private final Warnings _w;

   public ConfigurationBuilder(
         FlatJuniperCombinedParser parser, String text,
         Warnings warnings, Set<String> unimplementedFeatures) {
      _parser = parser;
      _text = text;
      _configuration = new JuniperConfiguration(unimplementedFeatures);
      _currentRoutingInstance = _configuration.getDefaultRoutingInstance();
      _termRouteFilters = new HashMap<>();
      _unimplementedFeatures = unimplementedFeatures;
      _w = warnings;
      _conjunctionPolicyIndex = 0;
      _disjunctionPolicyIndex = 0;
   }

   @Override
   public void enterA_application(A_applicationContext ctx) {
      String name = ctx.name.getText();
      _currentApplication = _configuration.getApplications().get(name);
      if (_currentApplication == null) {
         _currentApplication = new BaseApplication(name);
         _configuration.getApplications().put(name, _currentApplication);
      }
      _currentApplicationTerm = _currentApplication.getMainTerm();
   }

   @Override
   public void enterAa_term(Aa_termContext ctx) {
      String name = ctx.name.getText();
      _currentApplicationTerm = _currentApplication.getTerms().get(name);
      if (_currentApplicationTerm == null) {
         _currentApplicationTerm = new Term(name);
         _currentApplication.getTerms().put(name, _currentApplicationTerm);
      }
   }

   @Override
   public void enterB_group(B_groupContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      Map<String, NamedBgpGroup> namedBgpGroups = _currentRoutingInstance
            .getNamedBgpGroups();
      NamedBgpGroup namedBgpGroup = namedBgpGroups.get(name);
      if (namedBgpGroup == null) {
         namedBgpGroup = new NamedBgpGroup(name, definitionLine);
         namedBgpGroup.setParent(_currentBgpGroup);
         namedBgpGroups.put(name, namedBgpGroup);
      }
      _currentBgpGroup = namedBgpGroup;
   }

   @Override
   public void enterB_neighbor(B_neighborContext ctx) {
      if (ctx.IP_ADDRESS() != null) {
         Ip remoteAddress = new Ip(ctx.IP_ADDRESS().getText());
         Map<Ip, IpBgpGroup> ipBgpGroups = _currentRoutingInstance
               .getIpBgpGroups();
         IpBgpGroup ipBgpGroup = ipBgpGroups.get(remoteAddress);
         if (ipBgpGroup == null) {
            ipBgpGroup = new IpBgpGroup(remoteAddress);
            ipBgpGroup.setParent(_currentBgpGroup);
            ipBgpGroups.put(remoteAddress, ipBgpGroup);
         }
         _currentBgpGroup = ipBgpGroup;
      }
      else if (ctx.IPV6_ADDRESS() != null) {
         _currentBgpGroup.setIpv6(true);
         _currentBgpGroup = DUMMY_BGP_GROUP;
      }
   }

   @Override
   public void enterF_family(F_familyContext ctx) {
      if (ctx.INET() != null) {
         _currentFirewallFamily = Family.INET;
      }
      else if (ctx.INET6() != null) {
         _currentFirewallFamily = Family.INET6;
      }
      else if (ctx.MPLS() != null) {
         _currentFirewallFamily = Family.MPLS;
      }
   }

   @Override
   public void enterF_filter(F_filterContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      Map<String, FirewallFilter> filters = _configuration.getFirewallFilters();
      _currentFilter = filters.get(name);
      if (_currentFirewallFamily == null) {
         _currentFirewallFamily = Family.INET;
      }
      if (_currentFilter == null) {
         _currentFilter = new FirewallFilter(name, _currentFirewallFamily,
               definitionLine);
         filters.put(name, _currentFilter);
      }
   }

   @Override
   public void enterFf_term(Ff_termContext ctx) {
      String name = ctx.name.getText();
      Map<String, FwTerm> terms = _currentFilter.getTerms();
      _currentFwTerm = terms.get(name);
      if (_currentFwTerm == null) {
         _currentFwTerm = new FwTerm(name);
         terms.put(name, _currentFwTerm);
      }
   }

   @Override
   public void enterI_unit(I_unitContext ctx) {
      String unit = ctx.num.getText();
      int definitionLine = ctx.num.getLine();
      String unitFullName = _currentMasterInterface.getName() + "." + unit;
      Map<String, Interface> units = _currentMasterInterface.getUnits();
      _currentInterface = units.get(unitFullName);
      if (_currentInterface == null) {
         _currentInterface = new Interface(unitFullName, definitionLine);
         _currentInterface
               .setRoutingInstance(_currentRoutingInstance.getName());
         _currentInterface.setParent(_currentMasterInterface);
         units.put(unitFullName, _currentInterface);
      }
   }

   @Override
   public void enterIfi_address(Ifi_addressContext ctx) {
      Set<Prefix> allPrefixes = _currentInterface.getAllPrefixes();
      Prefix prefix;
      if (ctx.IP_PREFIX() != null) {
         prefix = new Prefix(ctx.IP_PREFIX().getText());
      }
      else if (ctx.IP_ADDRESS() != null) {
         Ip ip = new Ip(ctx.IP_ADDRESS().getText());
         prefix = new Prefix(ip, 32);
      }
      else {
         throw new BatfishException("Invalid or missing address");
      }
      _currentInterfacePrefix = prefix;
      if (_currentInterface.getPrimaryPrefix() == null) {
         _currentInterface.setPrimaryPrefix(prefix);
      }
      if (_currentInterface.getPreferredPrefix() == null) {
         _currentInterface.setPreferredPrefix(prefix);
      }
      allPrefixes.add(prefix);
      Ip ip = prefix.getAddress();
      _currentInterface.getAllPrefixIps().add(ip);
   }

   @Override
   public void enterIfia_vrrp_group(Ifia_vrrp_groupContext ctx) {
      int group = toInt(ctx.number);
      VrrpGroup currentVrrpGroup = _currentInterface.getVrrpGroups().get(group);
      if (currentVrrpGroup == null) {
         currentVrrpGroup = new VrrpGroup(group);
         currentVrrpGroup.setPreempt(DEFAULT_VRRP_PREEMPT);
         currentVrrpGroup.setPriority(DEFAULT_VRRP_PRIORITY);
         _currentInterface.getVrrpGroups().put(group, currentVrrpGroup);
      }
      _currentVrrpGroup = currentVrrpGroup;
   }

   @Override
   public void enterInt_named(Int_namedContext ctx) {
      Interface currentInterface;
      if (ctx.interface_id() == null) {
         currentInterface = _configuration.getGlobalMasterInterface();
      }
      else {
         String ifaceName = getInterfaceName(ctx.interface_id());
         Map<String, Interface> interfaces;
         String nodeDevicePrefix = "";
         if (ctx.interface_id().node == null) {
            interfaces = _configuration.getInterfaces();
         }
         else {
            String nodeDeviceName = ctx.interface_id().node.getText();
            nodeDevicePrefix = nodeDeviceName + ":";
            NodeDevice nodeDevice = _configuration.getNodeDevices()
                  .get(nodeDeviceName);
            if (nodeDevice == null) {
               nodeDevice = new NodeDevice(nodeDeviceName);
               _configuration.getNodeDevices().put(nodeDeviceName, nodeDevice);
            }
            interfaces = nodeDevice.getInterfaces();
         }
         currentInterface = interfaces.get(ifaceName);
         if (currentInterface == null) {
            String fullIfaceName = nodeDevicePrefix + ifaceName;
            int definitionLine = ctx.interface_id().getStart().getLine();
            currentInterface = new Interface(fullIfaceName, definitionLine);
            currentInterface
                  .setRoutingInstance(_currentRoutingInstance.getName());
            currentInterface
                  .setParent(_configuration.getGlobalMasterInterface());
            interfaces.put(fullIfaceName, currentInterface);
         }
      }
      _currentInterface = currentInterface;
      _currentMasterInterface = currentInterface;
   }

   @Override
   public void enterIs_interface(Is_interfaceContext ctx) {
      Map<String, Interface> interfaces = _configuration.getInterfaces();
      String unitFullName = null;
      String name = ctx.id.name.getText();
      int definitionLine = ctx.id.name.getLine();
      String unit = null;
      if (ctx.id.unit != null) {
         unit = ctx.id.unit.getText();
      }
      unitFullName = name + "." + unit;
      _currentIsisInterface = interfaces.get(name);
      if (_currentIsisInterface == null) {
         _currentIsisInterface = new Interface(name, definitionLine);
         _currentIsisInterface
               .setRoutingInstance(_currentRoutingInstance.getName());
         interfaces.put(name, _currentIsisInterface);
      }
      if (unit != null) {
         definitionLine = ctx.id.unit.getLine();
         Map<String, Interface> units = _currentIsisInterface.getUnits();
         _currentIsisInterface = units.get(unitFullName);
         if (_currentIsisInterface == null) {
            _currentIsisInterface = new Interface(unitFullName, definitionLine);
            _currentIsisInterface
                  .setRoutingInstance(_currentRoutingInstance.getName());
            units.put(unitFullName, _currentIsisInterface);
         }
      }
      _currentIsisInterface.getIsisSettings().setEnabled(true);
   }

   @Override
   public void enterIs_level(Is_levelContext ctx) {
      IsisSettings isisSettings = _currentRoutingInstance.getIsisSettings();
      int level = toInt(ctx.DEC());
      switch (level) {
      case 1:
         _currentIsisLevelSettings = isisSettings.getLevel1Settings();
         break;
      case 2:
         _currentIsisLevelSettings = isisSettings.getLevel2Settings();
         break;
      default:
         throw new BatfishException("invalid level: " + level);
      }
      _currentIsisLevelSettings.setEnabled(true);
   }

   @Override
   public void enterIsi_level(Isi_levelContext ctx) {
      int level = toInt(ctx.DEC());
      switch (level) {
      case 1:
         _currentIsisInterfaceLevelSettings = _currentIsisInterface
               .getIsisSettings().getLevel1Settings();
         break;
      case 2:
         _currentIsisInterfaceLevelSettings = _currentIsisInterface
               .getIsisSettings().getLevel2Settings();
         break;
      default:
         throw new BatfishException("invalid IS-IS level: " + level);
      }
      _currentIsisInterfaceLevelSettings.setEnabled(true);
   }

   @Override
   public void enterO_area(O_areaContext ctx) {
      Ip areaIp = new Ip(ctx.area.getText());
      Map<Ip, OspfArea> areas = _currentRoutingInstance.getOspfAreas();
      _currentArea = areas.get(areaIp);
      if (_currentArea == null) {
         _currentArea = new OspfArea(areaIp);
         areas.put(areaIp, _currentArea);
      }
   }

   @Override
   public void enterOa_interface(Oa_interfaceContext ctx) {
      Map<String, Interface> interfaces = _configuration.getInterfaces();
      String unitFullName = null;
      if (ctx.ALL() != null) {
         _currentOspfInterface = _currentRoutingInstance
               .getGlobalMasterInterface();
      }
      else if (ctx.ip != null) {
         Ip ip = new Ip(ctx.ip.getText());
         for (Interface iface : interfaces.values()) {
            for (Interface unit : iface.getUnits().values()) {
               if (unit.getAllPrefixIps().contains(ip)) {
                  _currentOspfInterface = unit;
                  unitFullName = unit.getName();
               }
            }
         }
         if (_currentOspfInterface == null) {
            throw new BatfishException(
                  "Could not find interface with ip address: " + ip.toString());
         }
      }
      else {
         _currentOspfInterface = initInterface(ctx.id);
         unitFullName = _currentOspfInterface.getName();
      }
      Ip currentArea = _currentArea.getAreaIp();
      if (ctx.oai_passive() != null) {
         _currentOspfInterface.getOspfPassiveAreas().add(currentArea);
      }
      else {
         Ip interfaceActiveArea = _currentOspfInterface.getOspfActiveArea();
         if (interfaceActiveArea != null
               && !currentArea.equals(interfaceActiveArea)) {
            throw new BatfishException("Interface: \"" + unitFullName.toString()
                  + "\" assigned to multiple active areas");
         }
         _currentOspfInterface.setOspfActiveArea(currentArea);
      }
   }

   @Override
   public void enterP_bgp(P_bgpContext ctx) {
      _currentBgpGroup = _currentRoutingInstance.getMasterBgpGroup();
   }

   @Override
   public void enterPo_community(Po_communityContext ctx) {
      String name = ctx.name.getText();
      Map<String, CommunityList> communityLists = _configuration
            .getCommunityLists();
      _currentCommunityList = communityLists.get(name);
      if (_currentCommunityList == null) {
         _currentCommunityList = new CommunityList(name);
         communityLists.put(name, _currentCommunityList);
      }
   }

   @Override
   public void enterPo_policy_statement(Po_policy_statementContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      Map<String, PolicyStatement> policyStatements = _configuration
            .getPolicyStatements();
      _currentPolicyStatement = policyStatements.get(name);
      if (_currentPolicyStatement == null) {
         _currentPolicyStatement = new PolicyStatement(name, definitionLine);
         policyStatements.put(name, _currentPolicyStatement);
      }
      _currentPsTerm = _currentPolicyStatement.getDefaultTerm();
      _currentPsThens = _currentPsTerm.getThens();
   }

   @Override
   public void enterPo_prefix_list(Po_prefix_listContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      Map<String, PrefixList> prefixLists = _configuration.getPrefixLists();
      _currentPrefixList = prefixLists.get(name);
      if (_currentPrefixList == null) {
         _currentPrefixList = new PrefixList(name, definitionLine);
         prefixLists.put(name, _currentPrefixList);
      }
   }

   @Override
   public void enterPops_term(Pops_termContext ctx) {
      String name = ctx.name.getText();
      Map<String, PsTerm> terms = _currentPolicyStatement.getTerms();
      _currentPsTerm = terms.get(name);
      if (_currentPsTerm == null) {
         _currentPsTerm = new PsTerm(name);
         terms.put(name, _currentPsTerm);
      }
      _currentPsThens = _currentPsTerm.getThens();
   }

   @Override
   public void enterPopsf_route_filter(Popsf_route_filterContext ctx) {
      _currentRouteFilter = _termRouteFilters.get(_currentPsTerm);
      if (_currentRouteFilter == null) {
         String rfName = _currentPolicyStatement.getName() + ":"
               + _currentPsTerm.getName();
         _currentRouteFilter = new RouteFilter(rfName);
         _termRouteFilters.put(_currentPsTerm, _currentRouteFilter);
         _configuration.getRouteFilters().put(rfName, _currentRouteFilter);
         PsFromRouteFilter from = new PsFromRouteFilter(rfName);
         _currentPsTerm.getFroms().add(from);
      }
      if (ctx.IP_PREFIX() != null) {
         _currentRouteFilterPrefix = new Prefix(ctx.IP_PREFIX().getText());
         _currentRouteFilter.setIpv4(true);
      }
      else if (ctx.IPV6_PREFIX() != null) {
         _currentRoute6FilterPrefix = new Prefix6(ctx.IPV6_PREFIX().getText());
         _currentRouteFilter.setIpv6(true);
      }
   }

   @Override
   public void enterPopsfrf_exact(Popsfrf_exactContext ctx) {
      if (_currentRouteFilterPrefix != null) { // ipv4
         Route4FilterLine line = new Route4FilterLineExact(
               _currentRouteFilterPrefix);
         _currentRouteFilterLine = _currentRouteFilter.insertLine(
               line,
               Route4FilterLine.class);
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Route6FilterLine line = new Route6FilterLineExact(
               _currentRoute6FilterPrefix);
         _currentRoute6FilterLine = _currentRouteFilter.insertLine(
               line,
               Route6FilterLine.class);
      }
   }

   @Override
   public void enterPopsfrf_longer(Popsfrf_longerContext ctx) {
      if (_currentRouteFilterPrefix != null) { // ipv4
         Route4FilterLine line = new Route4FilterLineLonger(
               _currentRouteFilterPrefix);
         _currentRouteFilterLine = _currentRouteFilter.insertLine(
               line,
               Route4FilterLine.class);
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Route6FilterLine line = new Route6FilterLineLonger(
               _currentRoute6FilterPrefix);
         _currentRoute6FilterLine = _currentRouteFilter.insertLine(
               line,
               Route6FilterLine.class);
      }
   }

   @Override
   public void enterPopsfrf_orlonger(Popsfrf_orlongerContext ctx) {
      if (_currentRouteFilterPrefix != null) { // ipv4
         Route4FilterLine line = new Route4FilterLineOrLonger(
               _currentRouteFilterPrefix);
         _currentRouteFilterLine = _currentRouteFilter.insertLine(
               line,
               Route4FilterLine.class);
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Route6FilterLine line = new Route6FilterLineOrLonger(
               _currentRoute6FilterPrefix);
         _currentRoute6FilterLine = _currentRouteFilter.insertLine(
               line,
               Route6FilterLine.class);
      }
   }

   @Override
   public void enterPopsfrf_prefix_length_range(
         Popsfrf_prefix_length_rangeContext ctx) {
      int minPrefixLength = toInt(ctx.low);
      int maxPrefixLength = toInt(ctx.high);
      if (_currentRouteFilterPrefix != null) { // ipv4
         Route4FilterLine line = new Route4FilterLineLengthRange(
               _currentRouteFilterPrefix, minPrefixLength, maxPrefixLength);
         _currentRouteFilterLine = _currentRouteFilter.insertLine(
               line,
               Route4FilterLine.class);
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Route6FilterLine line = new Route6FilterLineLengthRange(
               _currentRoute6FilterPrefix, minPrefixLength, maxPrefixLength);
         _currentRoute6FilterLine = _currentRouteFilter.insertLine(
               line,
               Route6FilterLine.class);
      }
   }

   @Override
   public void enterPopsfrf_then(Popsfrf_thenContext ctx) {
      if (_currentRouteFilterPrefix != null) { // ipv4
         Route4FilterLine line = _currentRouteFilterLine;
         _currentPsThens = line.getThens();
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Route6FilterLine line = _currentRoute6FilterLine;
         _currentPsThens = line.getThens();
      }
   }

   @Override
   public void enterPopsfrf_through(Popsfrf_throughContext ctx) {
      if (_currentRouteFilterPrefix != null) { // ipv4
         Prefix throughPrefix = new Prefix(ctx.IP_PREFIX().getText());
         Route4FilterLine line = new Route4FilterLineThrough(
               _currentRouteFilterPrefix, throughPrefix);
         _currentRouteFilterLine = _currentRouteFilter.insertLine(
               line,
               Route4FilterLine.class);
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Prefix6 throughPrefix6 = new Prefix6(ctx.IPV6_PREFIX().getText());
         Route6FilterLine line = new Route6FilterLineThrough(
               _currentRoute6FilterPrefix, throughPrefix6);
         _currentRoute6FilterLine = _currentRouteFilter.insertLine(
               line,
               Route6FilterLine.class);
      }
   }

   @Override
   public void enterPopsfrf_upto(Popsfrf_uptoContext ctx) {
      int maxPrefixLength = toInt(ctx.high);
      if (_currentRouteFilterPrefix != null) { // ipv4
         Route4FilterLine line = new Route4FilterLineUpTo(
               _currentRouteFilterPrefix, maxPrefixLength);
         _currentRouteFilterLine = _currentRouteFilter.insertLine(
               line,
               Route4FilterLine.class);
      }
      else if (_currentRoute6FilterPrefix != null) { // ipv6
         Route6FilterLine line = new Route6FilterLineUpTo(
               _currentRoute6FilterPrefix, maxPrefixLength);
         _currentRoute6FilterLine = _currentRouteFilter.insertLine(
               line,
               Route6FilterLine.class);
      }
   }

   @Override
   public void enterRi_named_routing_instance(
         Ri_named_routing_instanceContext ctx) {
      String name;
      name = ctx.name.getText();
      _currentRoutingInstance = _configuration.getRoutingInstances().get(name);
      if (_currentRoutingInstance == null) {
         _currentRoutingInstance = new RoutingInstance(name);
         _configuration.getRoutingInstances().put(
               name,
               _currentRoutingInstance);
      }
   }

   @Override
   public void enterRo_aggregate(Ro_aggregateContext ctx) {
      if (ctx.prefix != null) {
         Prefix prefix = new Prefix(ctx.IP_PREFIX().getText());
         Map<Prefix, AggregateRoute> aggregateRoutes = _currentRib
               .getAggregateRoutes();
         _currentAggregateRoute = aggregateRoutes.get(prefix);
         if (_currentAggregateRoute == null) {
            _currentAggregateRoute = new AggregateRoute(prefix);
            aggregateRoutes.put(prefix, _currentAggregateRoute);
         }
      }
      else {
         _currentAggregateRoute = DUMMY_AGGREGATE_ROUTE;
      }
   }

   @Override
   public void enterRo_generate(Ro_generateContext ctx) {
      if (ctx.IP_PREFIX() != null) {
         Prefix prefix = new Prefix(ctx.IP_PREFIX().getText());
         Map<Prefix, GeneratedRoute> generatedRoutes = _currentRib
               .getGeneratedRoutes();
         _currentGeneratedRoute = generatedRoutes.get(prefix);
         if (_currentGeneratedRoute == null) {
            _currentGeneratedRoute = new GeneratedRoute(prefix);
            generatedRoutes.put(prefix, _currentGeneratedRoute);
         }
      }
      else if (ctx.IPV6_PREFIX() != null) {
         // dummy generated route not added to configuration
         _currentGeneratedRoute = new GeneratedRoute(null);
         todo(ctx, F_IPV6);
      }
   }

   @Override
   public void enterRo_rib(Ro_ribContext ctx) {
      String name = ctx.name.getText();
      Map<String, RoutingInformationBase> ribs = _currentRoutingInstance
            .getRibs();
      _currentRib = ribs.get(name);
      if (_currentRib == null) {
         _currentRib = new RoutingInformationBase(name);
         ribs.put(name, _currentRib);
      }
   }

   @Override
   public void enterRos_route(Ros_routeContext ctx) {
      if (ctx.IP_PREFIX() != null) {
         Prefix prefix = new Prefix(ctx.IP_PREFIX().getText());
         Map<Prefix, StaticRoute> staticRoutes = _currentRib.getStaticRoutes();
         _currentStaticRoute = staticRoutes.get(prefix);
         if (_currentStaticRoute == null) {
            _currentStaticRoute = new StaticRoute(prefix);
            staticRoutes.put(prefix, _currentStaticRoute);
         }
      }
      else if (ctx.IPV6_PREFIX() != null) {
         _currentStaticRoute = DUMMY_STATIC_ROUTE;
      }
   }

   @Override
   public void enterS_firewall(S_firewallContext ctx) {
      _currentFirewallFamily = Family.INET;
   }

   @Override
   public void enterS_routing_options(S_routing_optionsContext ctx) {
      _currentRib = _currentRoutingInstance.getRibs()
            .get(RoutingInformationBase.RIB_IPV4_UNICAST);
   }

   @Override
   public void enterS_snmp(S_snmpContext ctx) {
      SnmpServer snmpServer = _currentRoutingInstance.getSnmpServer();
      if (snmpServer == null) {
         snmpServer = new SnmpServer();
         snmpServer.setVrf(_currentRoutingInstance.getName());
         _currentRoutingInstance.setSnmpServer(snmpServer);
      }
      _currentSnmpServer = snmpServer;
   }

   @Override
   public void enterSeik_gateway(Seik_gatewayContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      _currentIkeGateway = _configuration.getIkeGateways().get(name);
      if (_currentIkeGateway == null) {
         _currentIkeGateway = new IkeGateway(name, definitionLine);
         _configuration.getIkeGateways().put(name, _currentIkeGateway);
      }
   }

   @Override
   public void enterSeik_policy(Seik_policyContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      _currentIkePolicy = _configuration.getIkePolicies().get(name);
      if (_currentIkePolicy == null) {
         _currentIkePolicy = new IkePolicy(name, definitionLine);
         _configuration.getIkePolicies().put(name, _currentIkePolicy);
      }
   }

   @Override
   public void enterSeik_proposal(Seik_proposalContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      _currentIkeProposal = _configuration.getIkeProposals().get(name);
      if (_currentIkeProposal == null) {
         _currentIkeProposal = new IkeProposal(name, definitionLine);
         _configuration.getIkeProposals().put(name, _currentIkeProposal);
      }
   }

   @Override
   public void enterSeip_policy(Seip_policyContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      _currentIpsecPolicy = _configuration.getIpsecPolicies().get(name);
      if (_currentIpsecPolicy == null) {
         _currentIpsecPolicy = new IpsecPolicy(name, definitionLine);
         _configuration.getIpsecPolicies().put(name, _currentIpsecPolicy);
      }
   }

   @Override
   public void enterSeip_proposal(Seip_proposalContext ctx) {
      String name = ctx.name.getText();
      int definitionLine = ctx.name.getStart().getLine();
      _currentIpsecProposal = _configuration.getIpsecProposals().get(name);
      if (_currentIpsecProposal == null) {
         _currentIpsecProposal = new IpsecProposal(name, definitionLine);
         _configuration.getIpsecProposals().put(name, _currentIpsecProposal);
      }
   }

   @Override
   public void enterSeip_vpn(Seip_vpnContext ctx) {
      String name = ctx.name.getText();
      _currentIpsecVpn = _configuration.getIpsecVpns().get(name);
      if (_currentIpsecVpn == null) {
         _currentIpsecVpn = new IpsecVpn(name);
         _configuration.getIpsecVpns().put(name, _currentIpsecVpn);
      }
   }

   @Override
   public void enterSep_from_zone(Sep_from_zoneContext ctx) {
      if (ctx.from.JUNOS_HOST() != null && ctx.to.JUNOS_HOST() != null) {
         _w.redFlag(
               "Cannot create security policy from junos-host to junos-host");
      }
      else {
         String fromName = ctx.from.getText();
         String toName = ctx.to.getText();
         String policyName = "~FROM_ZONE~" + fromName + "~TO_ZONE~" + toName;
         if (ctx.from.JUNOS_HOST() == null) {
            _currentFromZone = _configuration.getZones().get(fromName);
            if (_currentFromZone == null) {
               _currentFromZone = new Zone(
                     fromName,
                     _configuration.getGlobalAddressBooks());
               _configuration.getZones().put(fromName, _currentFromZone);
               _configuration.getFirewallFilters().put(
                     _currentFromZone.getInboundFilter().getName(),
                     _currentFromZone.getInboundFilter());
            }
         }
         if (ctx.to.JUNOS_HOST() == null) {
            _currentToZone = _configuration.getZones().get(toName);
            if (_currentToZone == null) {
               _currentToZone = new Zone(
                     toName,
                     _configuration.getGlobalAddressBooks());
               _configuration.getFirewallFilters().put(
                     _currentToZone.getInboundFilter().getName(),
                     _currentToZone.getInboundFilter());
               _configuration.getZones().put(toName, _currentToZone);
            }
         }
         if (ctx.from.JUNOS_HOST() != null) {
            _currentFilter = _currentToZone.getFromHostFilter();
            if (_currentFilter == null) {
               _currentFilter = new FirewallFilter(policyName, Family.INET, -1);
               _configuration.getFirewallFilters().put(
                     policyName,
                     _currentFilter);
               _currentToZone.setFromHostFilter(_currentFilter);
            }
         }
         else if (ctx.to.JUNOS_HOST() != null) {
            _currentFilter = _currentFromZone.getToHostFilter();
            if (_currentFilter == null) {
               _currentFilter = new FirewallFilter(policyName, Family.INET, -1);
               _configuration.getFirewallFilters().put(
                     policyName,
                     _currentFilter);
               _currentFromZone.setToHostFilter(_currentFilter);
            }
         }
         else {
            _currentFilter = _currentFromZone.getToZonePolicies().get(toName);
            if (_currentFilter == null) {
               _currentFilter = new FirewallFilter(policyName, Family.INET, -1);
               _configuration.getFirewallFilters().put(
                     policyName,
                     _currentFilter);
               _currentFromZone.getToZonePolicies().put(toName, _currentFilter);
            }
         }
      }
   }

   @Override
   public void enterSepf_policy(Sepf_policyContext ctx) {
      String termName = ctx.name.getText();
      _currentFwTerm = _currentFilter.getTerms().get(termName);
      if (_currentFwTerm == null) {
         _currentFwTerm = new FwTerm(termName);
         _currentFilter.getTerms().put(termName, _currentFwTerm);
      }
   }

   @Override
   public void enterSez_security_zone(Sez_security_zoneContext ctx) {
      String zoneName = ctx.zone().getText();
      _currentZone = _configuration.getZones().get(zoneName);
      if (_currentZone == null) {
         _currentZone = new Zone(
               zoneName,
               _configuration.getGlobalAddressBooks());
         _configuration.getFirewallFilters().put(
               _currentZone.getInboundFilter().getName(),
               _currentZone.getInboundFilter());
         _configuration.getZones().put(zoneName, _currentZone);
      }
      _currentZoneInboundFilter = _currentZone.getInboundFilter();
   }

   @Override
   public void enterSezs_address_book(Sezs_address_bookContext ctx) {
      _currentAddressBook = _currentZone.getAddressBook();
   }

   @Override
   public void enterSezs_host_inbound_traffic(
         Sezs_host_inbound_trafficContext ctx) {
      if (_currentZoneInterface != null) {
         _currentZoneInboundFilter = _currentZone.getInboundInterfaceFilters()
               .get(_currentZoneInterface);
         if (_currentZoneInboundFilter == null) {
            String name = "~ZONE_INTERFACE_FILTER~" + _currentZone.getName()
                  + "~INTERFACE~" + _currentZoneInterface.getName();
            _currentZoneInboundFilter = new FirewallFilter(name, Family.INET,
                  -1);
            _configuration.getFirewallFilters().put(
                  name,
                  _currentZoneInboundFilter);
            _currentZone.getInboundInterfaceFilters().put(
                  _currentZoneInterface,
                  _currentZoneInboundFilter);
         }
      }
   }

   @Override
   public void enterSezs_interfaces(Sezs_interfacesContext ctx) {
      _currentZoneInterface = initInterface(ctx.interface_id());
      _currentZone.getInterfaces().add(_currentZoneInterface);
   }

   @Override
   public void enterSezsa_address_set(Sezsa_address_setContext ctx) {
      String name = ctx.name.getText();
      AddressBookEntry entry = _currentAddressBook.getEntries().get(name);
      if (entry == null) {
         entry = new AddressSetAddressBookEntry(name);
         _currentAddressBook.getEntries().put(name, entry);
      }
      try {
         _currentAddressSetAddressBookEntry = (AddressSetAddressBookEntry) entry;
      }
      catch (ClassCastException e) {
         throw new BatfishException(
               "Cannot create address-set address-book entry \"" + name
                     +
                     "\" because a different type of address-book entry with that name already exists",
               e);
      }
   }

   @Override
   public void enterSnmp_community(Snmp_communityContext ctx) {
      String community = ctx.comm.getText();
      SnmpCommunity snmpCommunity = _currentSnmpServer.getCommunities()
            .get(community);
      if (snmpCommunity == null) {
         snmpCommunity = new SnmpCommunity(community);
         _currentSnmpServer.getCommunities().put(community, snmpCommunity);
      }
      _currentSnmpCommunity = snmpCommunity;
   }

   @Override
   public void enterSy_tacplus_server(Sy_tacplus_serverContext ctx) {
      String hostname = ctx.hostname.getText();
      _configuration.getTacplusServers().add(hostname);
   }

   @Override
   public void enterSyn_server(Syn_serverContext ctx) {
      String hostname = ctx.hostname.getText();
      _configuration.getNtpServers().add(hostname);
   }

   @Override
   public void enterSys_host(Sys_hostContext ctx) {
      String hostname = ctx.hostname.getText();
      _configuration.getSyslogHosts().add(hostname);
   }

   @Override
   public void exitA_application(A_applicationContext ctx) {
      _currentApplication = null;
      _currentApplicationTerm = null;
   }

   @Override
   public void exitAa_term(Aa_termContext ctx) {
      _currentApplicationTerm = _currentApplication.getMainTerm();
   }

   @Override
   public void exitAat_destination_port(Aat_destination_portContext ctx) {
      SubRange subrange = toSubRange(ctx.subrange());
      _currentApplicationTerm.getLine().getDstPorts().add(subrange);
   }

   @Override
   public void exitAat_protocol(Aat_protocolContext ctx) {
      IpProtocol protocol = toIpProtocol(ctx.ip_protocol());
      _currentApplicationTerm.getLine().getIpProtocols().add(protocol);
   }

   @Override
   public void exitAat_source_port(Aat_source_portContext ctx) {
      SubRange subrange = toSubRange(ctx.subrange());
      _currentApplicationTerm.getLine().getSrcPorts().add(subrange);
   }

   @Override
   public void exitB_advertise_external(B_advertise_externalContext ctx) {
      _currentBgpGroup.setAdvertiseExternal(true);
   }

   @Override
   public void exitB_advertise_inactive(B_advertise_inactiveContext ctx) {
      _currentBgpGroup.setAdvertiseInactive(true);
   }

   @Override
   public void exitB_advertise_peer_as(B_advertise_peer_asContext ctx) {
      _currentBgpGroup.setAdvertisePeerAs(true);
   }

   @Override
   public void exitB_description(B_descriptionContext ctx) {
      String description = ctx.description().text.getText();
      _currentBgpGroup.setDescription(description);
   }

   @Override
   public void exitB_export(B_exportContext ctx) {
      Policy_expressionContext expr = ctx.expr;
      String name;
      int line;
      if (expr.variable() != null) {
         name = expr.variable().getText();
         line = expr.variable().getStart().getLine();
      }
      else {
         name = toComplexPolicyStatement(expr);
         line = expr.getStart().getLine();
      }
      _currentBgpGroup.getExportPolicies().put(name, line);
   }

   @Override
   public void exitB_import(B_importContext ctx) {
      Policy_expressionContext expr = ctx.expr;
      String name;
      int line;
      if (expr.variable() != null) {
         name = expr.variable().getText();
         line = expr.variable().getStart().getLine();
      }
      else {
         name = toComplexPolicyStatement(expr);
         line = expr.getStart().getLine();
      }
      _currentBgpGroup.getImportPolicies().put(name, line);
   }

   @Override
   public void exitB_local_address(B_local_addressContext ctx) {
      if (ctx.IP_ADDRESS() != null) {
         Ip localAddress = new Ip(ctx.IP_ADDRESS().getText());
         _currentBgpGroup.setLocalAddress(localAddress);
      }
   }

   @Override
   public void exitB_multihop(B_multihopContext ctx) {
      _currentBgpGroup.setEbgpMultihop(true);
   }

   @Override
   public void exitB_multipath(B_multipathContext ctx) {
      _currentBgpGroup.setMultipath(true);
      // TODO: support multiple-as function properly
   }

   @Override
   public void exitB_remove_private(B_remove_privateContext ctx) {
      _currentBgpGroup.setRemovePrivate(true);
   }

   @Override
   public void exitB_type(B_typeContext ctx) {
      if (ctx.INTERNAL() != null) {
         _currentBgpGroup.setType(BgpGroupType.INTERNAL);
      }
      else if (ctx.EXTERNAL() != null) {
         _currentBgpGroup.setType(BgpGroupType.EXTERNAL);
      }
   }

   @Override
   public void exitBl_loops(Bl_loopsContext ctx) {
      todo(ctx, F_BGP_LOCAL_AS_LOOPS);
      int loops = toInt(ctx.DEC());
      _currentBgpGroup.setLoops(loops);
   }

   @Override
   public void exitBl_number(Bl_numberContext ctx) {
      int localAs = toInt(ctx.as);
      _currentBgpGroup.setLocalAs(localAs);
   }

   @Override
   public void exitBl_private(Bl_privateContext ctx) {
      todo(ctx, F_BGP_LOCAL_AS_PRIVATE);
   }

   @Override
   public void exitBpa_as(Bpa_asContext ctx) {
      int peerAs = toInt(ctx.as);
      _currentBgpGroup.setPeerAs(peerAs);
   }

   @Override
   public void exitF_filter(F_filterContext ctx) {
      _currentFilter = null;
   }

   @Override
   public void exitFf_term(Ff_termContext ctx) {
      _currentFwTerm = null;
   }

   @Override
   public void exitFftf_destination_address(
         Fftf_destination_addressContext ctx) {
      if (ctx.IP_ADDRESS() != null || ctx.IP_PREFIX() != null) {
         Prefix prefix;
         if (ctx.IP_PREFIX() != null) {
            prefix = new Prefix(ctx.IP_PREFIX().getText());
         }
         else {
            prefix = new Prefix(
                  new Ip(ctx.IP_ADDRESS().getText()),
                  Prefix.MAX_PREFIX_LENGTH);
         }
         FwFrom from;
         if (ctx.EXCEPT() != null) {
            from = new FwFromDestinationAddressExcept(prefix);
         }
         else {
            from = new FwFromDestinationAddress(prefix);
         }
         _currentFwTerm.getFroms().add(from);
      }
   }

   @Override
   public void exitFftf_destination_port(Fftf_destination_portContext ctx) {
      if (ctx.port() != null) {
         int port = getPortNumber(ctx.port());
         SubRange subrange = new SubRange(port, port);
         FwFrom from = new FwFromDestinationPort(subrange);
         _currentFwTerm.getFroms().add(from);
      }
      else if (ctx.range() != null) {
         for (SubrangeContext subrangeContext : ctx.range().range_list) {
            SubRange subrange = toSubRange(subrangeContext);
            FwFrom from = new FwFromDestinationPort(subrange);
            _currentFwTerm.getFroms().add(from);
         }
      }
   }

   @Override
   public void exitFftf_destination_prefix_list(
         Fftf_destination_prefix_listContext ctx) {
      String name = ctx.name.getText();
      // temporary
      if (_currentFilter.getFamily() != Family.INET) {
         _configuration.getIgnoredPrefixLists().add(name);
      }
      FwFrom from;
      if (ctx.EXCEPT() != null) {
         from = new FwFromDestinationPrefixListExcept(name);
      }
      else {
         from = new FwFromDestinationPrefixList(name);
      }
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_first_fragment(Fftf_first_fragmentContext ctx) {
      SubRange subRange = new SubRange(0, 0);
      FwFrom from = new FwFromFragmentOffset(subRange, false);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_fragment_offset(Fftf_fragment_offsetContext ctx) {
      SubRange subRange = toSubRange(ctx.subrange());
      FwFrom from = new FwFromFragmentOffset(subRange, false);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_fragment_offset_except(
         Fftf_fragment_offset_exceptContext ctx) {
      SubRange subRange = toSubRange(ctx.subrange());
      FwFrom from = new FwFromFragmentOffset(subRange, true);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_icmp_code(Fftf_icmp_codeContext ctx) {
      if (_currentFirewallFamily == Family.INET6) {
         // TODO: support icmpv6
         return;
      }
      SubRange icmpCodeRange;
      if (ctx.subrange() != null) {
         icmpCodeRange = toSubRange(ctx.subrange());
      }
      else if (ctx.icmp_code() != null) {
         int icmpCode = toIcmpCode(ctx.icmp_code());
         icmpCodeRange = new SubRange(icmpCode, icmpCode);
      }
      else {
         throw new BatfishException("Invalid icmp-code");
      }
      FwFrom from = new FwFromIcmpCode(icmpCodeRange);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_icmp_type(Fftf_icmp_typeContext ctx) {
      if (_currentFirewallFamily == Family.INET6) {
         // TODO: support icmpv6
         return;
      }
      SubRange icmpTypeRange;
      if (ctx.subrange() != null) {
         icmpTypeRange = toSubRange(ctx.subrange());
      }
      else if (ctx.icmp_type() != null) {
         int icmpType = toIcmpType(ctx.icmp_type());
         icmpTypeRange = new SubRange(icmpType, icmpType);
      }
      else {
         throw new BatfishException("Invalid icmp-type");
      }
      FwFrom from = new FwFromIcmpType(icmpTypeRange);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_is_fragment(Fftf_is_fragmentContext ctx) {
      SubRange subRange = new SubRange(0, 0);
      FwFrom from = new FwFromFragmentOffset(subRange, true);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_packet_length(Fftf_packet_lengthContext ctx) {
      List<SubRange> range = toRange(ctx.range());
      FwFrom from = new FwFromPacketLength(range, false);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_packet_length_except(
         Fftf_packet_length_exceptContext ctx) {
      List<SubRange> range = toRange(ctx.range());
      FwFrom from = new FwFromPacketLength(range, true);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_port(Fftf_portContext ctx) {
      if (ctx.port() != null) {
         int port = getPortNumber(ctx.port());
         SubRange subrange = new SubRange(port, port);
         FwFrom from = new FwFromPort(subrange);
         _currentFwTerm.getFroms().add(from);
      }
      else if (ctx.range() != null) {
         for (SubrangeContext subrangeContext : ctx.range().range_list) {
            SubRange subrange = toSubRange(subrangeContext);
            FwFrom from = new FwFromPort(subrange);
            _currentFwTerm.getFroms().add(from);
         }
      }
   }

   @Override
   public void exitFftf_prefix_list(Fftf_prefix_listContext ctx) {
      String name = ctx.name.getText();
      // temporary
      if (_currentFilter.getFamily() != Family.INET) {
         _configuration.getIgnoredPrefixLists().add(name);
      }
      FwFromPrefixList from = new FwFromPrefixList(name);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_protocol(Fftf_protocolContext ctx) {
      IpProtocol protocol = toIpProtocol(ctx.ip_protocol());
      FwFrom from = new FwFromProtocol(protocol);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_source_address(Fftf_source_addressContext ctx) {
      if (ctx.IP_ADDRESS() != null || ctx.IP_PREFIX() != null) {
         Prefix prefix;
         if (ctx.IP_PREFIX() != null) {
            prefix = new Prefix(ctx.IP_PREFIX().getText());
         }
         else {
            prefix = new Prefix(
                  new Ip(ctx.IP_ADDRESS().getText()),
                  Prefix.MAX_PREFIX_LENGTH);
         }
         FwFrom from;
         if (ctx.EXCEPT() != null) {
            from = new FwFromSourceAddressExcept(prefix);
         }
         else {
            from = new FwFromSourceAddress(prefix);
         }
         _currentFwTerm.getFroms().add(from);
      }
   }

   @Override
   public void exitFftf_source_port(Fftf_source_portContext ctx) {
      if (ctx.port() != null) {
         int port = getPortNumber(ctx.port());
         SubRange subrange = new SubRange(port, port);
         FwFrom from = new FwFromSourcePort(subrange);
         _currentFwTerm.getFroms().add(from);
      }
      else if (ctx.range() != null) {
         for (SubrangeContext subrangeContext : ctx.range().range_list) {
            SubRange subrange = toSubRange(subrangeContext);
            FwFrom from = new FwFromSourcePort(subrange);
            _currentFwTerm.getFroms().add(from);
         }
      }
   }

   @Override
   public void exitFftf_source_prefix_list(Fftf_source_prefix_listContext ctx) {
      String name = ctx.name.getText();
      // temporary
      if (_currentFilter.getFamily() != Family.INET) {
         _configuration.getIgnoredPrefixLists().add(name);
      }
      FwFrom from;
      if (ctx.EXCEPT() != null) {
         from = new FwFromSourcePrefixListExcept(name);
      }
      else {
         from = new FwFromSourcePrefixList(name);
      }
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_tcp_established(Fftf_tcp_establishedContext ctx) {
      List<TcpFlags> tcpFlags = new ArrayList<>();
      TcpFlags alt1 = new TcpFlags();
      alt1.setUseAck(true);
      alt1.setAck(true);
      tcpFlags.add(alt1);
      TcpFlags alt2 = new TcpFlags();
      alt2.setUseRst(true);
      alt2.setRst(true);
      tcpFlags.add(alt2);
      FwFrom from = new FwFromTcpFlags(tcpFlags);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_tcp_flags(Fftf_tcp_flagsContext ctx) {
      List<TcpFlags> tcpFlags = toTcpFlags(ctx.tcp_flags());
      FwFrom from = new FwFromTcpFlags(tcpFlags);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftf_tcp_initial(Fftf_tcp_initialContext ctx) {
      List<TcpFlags> tcpFlags = new ArrayList<>();
      TcpFlags alt1 = new TcpFlags();
      alt1.setUseAck(true);
      alt1.setAck(false);
      alt1.setUseSyn(true);
      alt1.setSyn(true);
      tcpFlags.add(alt1);
      FwFrom from = new FwFromTcpFlags(tcpFlags);
      _currentFwTerm.getFroms().add(from);
   }

   @Override
   public void exitFftt_accept(Fftt_acceptContext ctx) {
      _currentFwTerm.getThens().add(FwThenAccept.INSTANCE);
   }

   @Override
   public void exitFftt_discard(Fftt_discardContext ctx) {
      _currentFwTerm.getThens().add(FwThenDiscard.INSTANCE);
   }

   @Override
   public void exitFftt_next_ip(Fftt_next_ipContext ctx) {
      Prefix nextPrefix;
      if (ctx.ip != null) {
         Ip nextIp = new Ip(ctx.ip.getText());
         nextPrefix = new Prefix(nextIp, 32);
      }
      else {
         nextPrefix = new Prefix(ctx.prefix.getText());
      }
      FwThenNextIp then = new FwThenNextIp(nextPrefix);
      _currentFwTerm.getThens().add(then);
      _currentFwTerm.getThens().add(FwThenAccept.INSTANCE);
      _currentFilter.setRoutingPolicy(true);
   }

   @Override
   public void exitFftt_next_term(Fftt_next_termContext ctx) {
      _currentFwTerm.getThens().add(FwThenNextTerm.INSTANCE);
   }

   @Override
   public void exitFftt_nop(Fftt_nopContext ctx) {
      _currentFwTerm.getThens().add(FwThenNop.INSTANCE);
   }

   @Override
   public void exitFftt_reject(Fftt_rejectContext ctx) {
      _currentFwTerm.getThens().add(FwThenDiscard.INSTANCE);
   }

   @Override
   public void exitFftt_routing_instance(Fftt_routing_instanceContext ctx) {
      // TODO: implement
      _w.unimplemented(
            ConfigurationBuilder.F_FIREWALL_TERM_THEN_ROUTING_INSTANCE);
      _currentFwTerm.getThens().add(FwThenDiscard.INSTANCE);
   }

   @Override
   public void exitFlat_juniper_configuration(
         Flat_juniper_configurationContext ctx) {
      if (_hasZones) {
         if (_defaultCrossZoneAction == null) {
            _defaultCrossZoneAction = LineAction.REJECT;
         }
         _configuration.setDefaultCrossZoneAction(_defaultCrossZoneAction);
         _configuration.setDefaultInboundAction(LineAction.REJECT);
      }
      else {
         _configuration.setDefaultInboundAction(LineAction.ACCEPT);
      }
   }

   @Override
   public void exitI_disable(I_disableContext ctx) {
      _currentInterface.setActive(false);
   }

   @Override
   public void exitI_enable(I_enableContext ctx) {
      _currentInterface.setActive(true);
   }

   @Override
   public void exitI_mtu(I_mtuContext ctx) {
      int size = toInt(ctx.size);
      _currentInterface.setMtu(size);
   }

   @Override
   public void exitI_unit(I_unitContext ctx) {
      _currentInterface = _currentMasterInterface;
   }

   @Override
   public void exitIfi_address(Ifi_addressContext ctx) {
      _currentInterfacePrefix = null;
   }

   @Override
   public void exitIfi_filter(Ifi_filterContext ctx) {
      FilterContext filter = ctx.filter();
      if (filter.direction() != null) {
         String name = filter.name.getText();
         int line = filter.name.getStart().getLine();
         DirectionContext direction = filter.direction();
         if (direction.INPUT() != null) {
            _currentInterface.setIncomingFilter(name);
            _currentInterface.setIncomingFilterLine(line);
         }
         else if (direction.OUTPUT() != null) {
            _currentInterface.setOutgoingFilter(name);
            _currentInterface.setOutgoingFilterLine(line);
         }
      }
   }

   @Override
   public void exitIfia_preferred(Ifia_preferredContext ctx) {
      _currentInterface.setPreferredPrefix(_currentInterfacePrefix);
   }

   @Override
   public void exitIfia_primary(Ifia_primaryContext ctx) {
      _currentInterface.setPrimaryPrefix(_currentInterfacePrefix);
   }

   @Override
   public void exitIfia_vrrp_group(Ifia_vrrp_groupContext ctx) {
      _currentVrrpGroup = null;
   }

   @Override
   public void exitIfiav_preempt(Ifiav_preemptContext ctx) {
      _currentVrrpGroup.setPreempt(true);
   }

   @Override
   public void exitIfiav_priority(Ifiav_priorityContext ctx) {
      int priority = toInt(ctx.priority);
      _currentVrrpGroup.setPriority(priority);
   }

   @Override
   public void exitIfiav_virtual_address(Ifiav_virtual_addressContext ctx) {
      Ip virtualAddress = new Ip(ctx.IP_ADDRESS().getText());
      int prefixLength = _currentInterfacePrefix.getPrefixLength();
      _currentVrrpGroup
            .setVirtualAddress(new Prefix(virtualAddress, prefixLength));
   }

   @Override
   public void exitIfiso_address(Ifiso_addressContext ctx) {
      IsoAddress address = new IsoAddress(ctx.ISO_ADDRESS().getText());
      _currentInterface.setIsoAddress(address);
   }

   @Override
   public void exitInt_named(Int_namedContext ctx) {
      _currentInterface = null;
      _currentMasterInterface = null;
   }

   @Override
   public void exitIs_export(Is_exportContext ctx) {
      Set<String> policies = new LinkedHashSet<>();
      for (VariableContext policy : ctx.policies) {
         policies.add(policy.getText());
      }
      _currentRoutingInstance.getIsisSettings().getExportPolicies()
            .addAll(policies);
   }

   @Override
   public void exitIs_interface(Is_interfaceContext ctx) {
      _currentIsisInterface = null;
   }

   @Override
   public void exitIs_level(Is_levelContext ctx) {
      _currentIsisLevelSettings = null;
   }

   @Override
   public void exitIs_no_ipv4_routing(Is_no_ipv4_routingContext ctx) {
      _currentRoutingInstance.getIsisSettings().setNoIpv4Routing(true);
   }

   @Override
   public void exitIsi_level(Isi_levelContext ctx) {
      _currentIsisInterfaceLevelSettings = null;
   }

   @Override
   public void exitIsi_passive(Isi_passiveContext ctx) {
      _currentIsisInterface.getIsisSettings().setPassive(true);
   }

   @Override
   public void exitIsi_point_to_point(Isi_point_to_pointContext ctx) {
      _currentIsisInterface.getIsisSettings().setPointToPoint(true);
   }

   @Override
   public void exitIsil_enable(Isil_enableContext ctx) {
      _currentIsisInterfaceLevelSettings.setEnabled(true);
   }

   @Override
   public void exitIsil_metric(Isil_metricContext ctx) {
      int metric = toInt(ctx.DEC());
      _currentIsisInterfaceLevelSettings.setMetric(metric);
   }

   @Override
   public void exitIsil_te_metric(Isil_te_metricContext ctx) {
      int teMetric = toInt(ctx.DEC());
      _currentIsisInterfaceLevelSettings.setTeMetric(teMetric);
   }

   @Override
   public void exitIsl_disable(Isl_disableContext ctx) {
      _currentIsisLevelSettings.setEnabled(false);
   }

   @Override
   public void exitIsl_wide_metrics_only(Isl_wide_metrics_onlyContext ctx) {
      _currentIsisLevelSettings.setWideMetricsOnly(true);
   }

   @Override
   public void exitIst_credibility_protocol_preference(
         Ist_credibility_protocol_preferenceContext ctx) {
      _currentRoutingInstance.getIsisSettings()
            .setTrafficEngineeringCredibilityProtocolPreference(true);
   }

   @Override
   public void exitIst_family_shortcuts(Ist_family_shortcutsContext ctx) {
      if (ctx.INET6() != null) {
         todo(ctx, F_IPV6);
      }
      else { // ipv4
         _currentRoutingInstance.getIsisSettings()
               .setTrafficEngineeringShortcuts(true);
      }
   }

   @Override
   public void exitO_area(O_areaContext ctx) {
      _currentArea = null;
   }

   @Override
   public void exitO_export(O_exportContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentRoutingInstance.getOspfExportPolicies().put(name, line);
   }

   @Override
   public void exitOa_interface(Oa_interfaceContext ctx) {
      _currentOspfInterface = null;
   }

   @Override
   public void exitP_bgp(P_bgpContext ctx) {
      _currentBgpGroup = null;
   }

   @Override
   public void exitPo_community(Po_communityContext ctx) {
      _currentCommunityList = null;
   }

   @Override
   public void exitPo_policy_statement(Po_policy_statementContext ctx) {
      _currentPolicyStatement = null;
   }

   public void exitPo_prefix_list(Po_prefix_listContext ctx) {
      _currentPrefixList = null;
   }

   @Override
   public void exitPoc_invert_match(Poc_invert_matchContext ctx) {
      _currentCommunityList.setInvertMatch(true);
   }

   @Override
   public void exitPoc_members(Poc_membersContext ctx) {
      if (ctx.community_regex() != null) {
         String text = ctx.community_regex().getText();
         _currentCommunityList.getLines().add(new CommunityListLine(text));
         if (text.matches("[0-9]+:[0-9]+")) {
            long communityVal = CommonUtil.communityStringToLong(text);
            _configuration.getAllStandardCommunities().add(communityVal);
         }
      }
      else if (ctx.extended_community_regex() != null) {
         String text = ctx.extended_community_regex().getText();
         _currentCommunityList.getLines().add(new CommunityListLine(text));
      }
      else if (ctx.standard_community() != null) {
         long communityVal = toCommunityLong(ctx.standard_community());
         _configuration.getAllStandardCommunities().add(communityVal);
         String communityStr = org.batfish.common.util.CommonUtil
               .longToCommunity(communityVal);
         _currentCommunityList.getLines()
               .add(new CommunityListLine(communityStr));
      }
      else if (ctx.extended_community() != null) {
         long communityVal = toCommunityLong(ctx.extended_community());
         String communityStr = org.batfish.common.util.CommonUtil
               .longToCommunity(communityVal);
         _currentCommunityList.getLines()
               .add(new CommunityListLine(communityStr));
      }
      else if (ctx.invalid_community_regex() != null) {
         String text = ctx.invalid_community_regex().getText();
         _currentCommunityList.getLines().add(new CommunityListLine(text));
         _w.redFlag(ctx.getStart().getLine() + ": In community-list '"
               + _currentCommunityList.getName()
               + "': Invalid or unsupported community regex: '" + text + "'");
      }
   }

   @Override
   public void exitPoplt_ip6(Poplt_ip6Context ctx) {
      _currentPrefixList.setIpv6(true);
      todo(ctx, F_IPV6);
   }

   @Override
   public void exitPoplt_network(Poplt_networkContext ctx) {
      Prefix prefix = new Prefix(ctx.network.getText());
      _currentPrefixList.getPrefixes().add(prefix);
   }

   @Override
   public void exitPoplt_network6(Poplt_network6Context ctx) {
      _currentPrefixList.setIpv6(true);
      todo(ctx, F_IPV6);
   }

   @Override
   public void exitPops_common(Pops_commonContext ctx) {
      _currentPsTerm = null;
      _currentPsThens = null;
   }

   @Override
   public void exitPopsf_as_path(Popsf_as_pathContext ctx) {
      String name = ctx.name.getText();
      PsFromAsPath fromAsPath = new PsFromAsPath(name);
      _currentPsTerm.getFroms().add(fromAsPath);
   }

   @Override
   public void exitPopsf_color(Popsf_colorContext ctx) {
      int color = toInt(ctx.color);
      PsFromColor fromColor = new PsFromColor(color);
      _currentPsTerm.getFroms().add(fromColor);
   }

   @Override
   public void exitPopsf_community(Popsf_communityContext ctx) {
      String name = ctx.name.getText();
      PsFromCommunity fromCommunity = new PsFromCommunity(name);
      _currentPsTerm.getFroms().add(fromCommunity);
   }

   @Override
   public void exitPopsf_family(Popsf_familyContext ctx) {
      PsFrom from;
      if (ctx.INET() != null) {
         from = new PsFromFamilyInet();
      }
      else if (ctx.INET6() != null) {
         from = new PsFromFamilyInet6();
      }
      else {
         throw new BatfishException("Unsupported family: " + ctx.getText());
      }
      _currentPsTerm.getFroms().add(from);
   }

   @Override
   public void exitPopsf_interface(Popsf_interfaceContext ctx) {
      String name = ctx.id.name.getText();
      int definitionLine = ctx.id.name.getLine();
      String unit = null;
      if (ctx.id.unit != null) {
         unit = ctx.id.unit.getText();
         definitionLine = ctx.id.unit.getLine();
      }
      String unitFullName = name + "." + unit;
      Map<String, Interface> interfaces = _configuration.getInterfaces();
      Interface iface = interfaces.get(name);
      if (iface == null) {
         iface = new Interface(name, definitionLine);
         iface.setRoutingInstance(_currentRoutingInstance.getName());
         interfaces.put(name, iface);
      }
      PsFrom from;
      if (unit != null) {
         Map<String, Interface> units = iface.getUnits();
         iface = units.get(unitFullName);
         if (iface == null) {
            iface = new Interface(unitFullName, definitionLine);
            iface.setRoutingInstance(_currentRoutingInstance.getName());
            units.put(unitFullName, iface);
         }
         from = new PsFromInterface(unitFullName);
      }
      else {
         from = new PsFromInterface(name);
      }
      _currentPsTerm.getFroms().add(from);
   }

   @Override
   public void exitPopsf_local_preference(Popsf_local_preferenceContext ctx) {
      int localPreference = toInt(ctx.localpref);
      PsFromLocalPreference fromLocalPreference = new PsFromLocalPreference(
            localPreference);
      _currentPsTerm.getFroms().add(fromLocalPreference);
   }

   @Override
   public void exitPopsf_metric(Popsf_metricContext ctx) {
      int metric = toInt(ctx.metric);
      PsFromMetric fromMetric = new PsFromMetric(metric);
      _currentPsTerm.getFroms().add(fromMetric);
   }

   @Override
   public void exitPopsf_policy(Popsf_policyContext ctx) {
      String policyName = toComplexPolicyStatement(ctx.policy_expression());
      PsFrom from = new PsFromPolicyStatement(policyName);
      _currentPsTerm.getFroms().add(from);
   }

   @Override
   public void exitPopsf_prefix_list(Popsf_prefix_listContext ctx) {
      String name = ctx.name.getText();
      PsFrom from = new PsFromPrefixList(name);
      _currentPsTerm.getFroms().add(from);
   }

   @Override
   public void exitPopsf_prefix_list_filter(
         Popsf_prefix_list_filterContext ctx) {
      String name = ctx.name.getText();
      PsFrom from;
      if (ctx.popsfpl_exact() != null) {
         from = new PsFromPrefixList(name);
      }
      else if (ctx.popsfpl_longer() != null) {
         from = new PsFromPrefixListFilterLonger(name);
      }
      else if (ctx.popsfpl_orlonger() != null) {
         from = new PsFromPrefixListFilterOrLonger(name);
      }
      else {
         throw new BatfishException(
               "Invalid prefix-list-filter length specification");
      }
      _currentPsTerm.getFroms().add(from);
   }

   @Override
   public void exitPopsf_protocol(Popsf_protocolContext ctx) {
      RoutingProtocol protocol = toRoutingProtocol(ctx.protocol);
      PsFromProtocol fromProtocol = new PsFromProtocol(protocol);
      _currentPsTerm.getFroms().add(fromProtocol);
   }

   @Override
   public void exitPopsf_route_filter(Popsf_route_filterContext ctx) {
      _currentRouteFilterPrefix = null;
      _currentRoute6FilterPrefix = null;
      _currentRouteFilter = null;
      _currentRouteFilterLine = null;
      _currentRoute6FilterLine = null;
   }

   @Override
   public void exitPopsfrf_then(Popsfrf_thenContext ctx) {
      _currentPsThens = _currentPsTerm.getThens();
   }

   @Override
   public void exitPopst_accept(Popst_acceptContext ctx) {
      _currentPsThens.add(PsThenAccept.INSTANCE);
   }

   @Override
   public void exitPopst_community_add(Popst_community_addContext ctx) {
      String name = ctx.name.getText();
      PsThenCommunityAdd then = new PsThenCommunityAdd(name, _configuration);
      _currentPsThens.add(then);
   }

   @Override
   public void exitPopst_community_delete(Popst_community_deleteContext ctx) {
      String name = ctx.name.getText();
      PsThenCommunityDelete then = new PsThenCommunityDelete(
            name,
            _configuration);
      _currentPsThens.add(then);
   }

   @Override
   public void exitPopst_community_set(Popst_community_setContext ctx) {
      String name = ctx.name.getText();
      PsThenCommunitySet then = new PsThenCommunitySet(name, _configuration);
      _currentPsThens.add(then);
   }

   @Override
   public void exitPopst_local_preference(Popst_local_preferenceContext ctx) {
      int localPreference = toInt(ctx.localpref);
      PsThenLocalPreference then = new PsThenLocalPreference(localPreference);
      _currentPsThens.add(then);
   }

   @Override
   public void exitPopst_metric(Popst_metricContext ctx) {
      int metric = toInt(ctx.metric);
      PsThenMetric then = new PsThenMetric(metric);
      _currentPsThens.add(then);
   }

   @Override
   public void exitPopst_next_hop(Popst_next_hopContext ctx) {
      PsThen then;
      if (ctx.IP_ADDRESS() != null) {
         Ip nextHopIp = new Ip(ctx.IP_ADDRESS().getText());
         then = new PsThenNextHopIp(nextHopIp);
      }
      else {
         todo(ctx, F_POLICY_TERM_THEN_NEXT_HOP);
         return;
      }
      _currentPsThens.add(then);
   }

   @Override
   public void exitPopst_next_policy(Popst_next_policyContext ctx) {
      _currentPsThens.add(PsThenNextPolicy.INSTANCE);
   }

   @Override
   public void exitPopst_reject(Popst_rejectContext ctx) {
      _currentPsThens.add(PsThenReject.INSTANCE);
   }

   @Override
   public void exitRi_interface(Ri_interfaceContext ctx) {
      Interface iface = initInterface(ctx.id);
      iface.setRoutingInstance(_currentRoutingInstance.getName());
   }

   @Override
   public void exitRi_named_routing_instance(
         Ri_named_routing_instanceContext ctx) {
      _currentRoutingInstance = _configuration.getDefaultRoutingInstance();
   }

   @Override
   public void exitRo_aggregate(Ro_aggregateContext ctx) {
      _currentAggregateRoute = null;
   }

   @Override
   public void exitRo_autonomous_system(Ro_autonomous_systemContext ctx) {
      if (ctx.as != null) {
         int as = toInt(ctx.as);
         _currentRoutingInstance.setAs(as);
      }
   }

   @Override
   public void exitRo_generate(Ro_generateContext ctx) {
      _currentGeneratedRoute = null;
   }

   @Override
   public void exitRo_router_id(Ro_router_idContext ctx) {
      Ip id = new Ip(ctx.id.getText());
      _currentRoutingInstance.setRouterId(id);
   }

   @Override
   public void exitRo_static(Ro_staticContext ctx) {
      _currentStaticRoute = null;
   }

   @Override
   public void exitRoa_community(Roa_communityContext ctx) {
      long community = CommonUtil
            .communityStringToLong(ctx.COMMUNITY_LITERAL().getText());
      _configuration.getAllStandardCommunities().add(community);
      _currentAggregateRoute.getCommunities().add(community);
   }

   @Override
   public void exitRoa_preference(Roa_preferenceContext ctx) {
      int preference = toInt(ctx.preference);
      _currentAggregateRoute.setPreference(preference);
   }

   @Override
   public void exitRoa_tag(Roa_tagContext ctx) {
      int tag = toInt(ctx.tag);
      _currentAggregateRoute.setTag(tag);
   }

   @Override
   public void exitRoaa_path(Roaa_pathContext ctx) {
      AsPath asPath = toAsPath(ctx.path);
      _currentAggregateRoute.setAsPath(asPath);
   }

   @Override
   public void exitRof_export(Rof_exportContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _configuration.getDefaultRoutingInstance()
            .setForwardingTableExportPolicy(name);
      _configuration.getDefaultRoutingInstance()
            .setForwardingTableExportPolicyLine(line);
   }

   @Override
   public void exitRog_metric(Rog_metricContext ctx) {
      int metric = toInt(ctx.metric);
      _currentGeneratedRoute.setMetric(metric);
   }

   @Override
   public void exitRog_policy(Rog_policyContext ctx) {
      if (_currentGeneratedRoute != null) { // not ipv6
         String policy = ctx.policy.getText();
         int line = ctx.policy.getStart().getLine();
         _currentGeneratedRoute.getPolicies().put(policy, line);
      }
   }

   @Override
   public void exitRosr_discard(Rosr_discardContext ctx) {
      _currentStaticRoute.setDrop(true);
   }

   @Override
   public void exitRosr_metric(Rosr_metricContext ctx) {
      int metric = toInt(ctx.metric);
      _currentStaticRoute.setMetric(metric);
   }

   @Override
   public void exitRosr_next_hop(Rosr_next_hopContext ctx) {
      if (ctx.IP_ADDRESS() != null) {
         Ip nextHopIp = new Ip(ctx.IP_ADDRESS().getText());
         _currentStaticRoute.setNextHopIp(nextHopIp);
      }
      else if (ctx.interface_id() != null) {
         initInterface(ctx.interface_id());
         _currentStaticRoute.setNextHopInterface(ctx.interface_id().getText());
      }
   }

   @Override
   public void exitRosr_reject(Rosr_rejectContext ctx) {
      _currentStaticRoute.setDrop(true);
   }

   @Override
   public void exitRosr_tag(Rosr_tagContext ctx) {
      int tag = toInt(ctx.tag);
      _currentStaticRoute.setTag(tag);
   }

   @Override
   public void exitS_firewall(S_firewallContext ctx) {
      _currentFirewallFamily = null;
   }

   @Override
   public void exitS_routing_options(S_routing_optionsContext ctx) {
      _currentRib = null;
   }

   @Override
   public void exitS_snmp(S_snmpContext ctx) {
      _currentSnmpServer = null;
   }

   @Override
   public void exitSe_zones(Se_zonesContext ctx) {
      _hasZones = true;
   }

   @Override
   public void exitSeik_gateway(Seik_gatewayContext ctx) {
      _currentIkeGateway = null;
   }

   @Override
   public void exitSeik_policy(Seik_policyContext ctx) {
      _currentIkePolicy = null;
   }

   @Override
   public void exitSeik_proposal(Seik_proposalContext ctx) {
      _currentIkeProposal = null;
   }

   @Override
   public void exitSeikg_address(Seikg_addressContext ctx) {
      Ip ip = new Ip(ctx.IP_ADDRESS().getText());
      _currentIkeGateway.setAddress(ip);
   }

   @Override
   public void exitSeikg_external_interface(
         Seikg_external_interfaceContext ctx) {
      Interface_idContext interfaceId = ctx.interface_id();
      int line = ctx.interface_id().getStart().getLine();
      Interface iface = initInterface(interfaceId);
      _currentIkeGateway.setExternalInterface(iface);
      _currentIkeGateway.setExternalInterfaceLine(line);
   }

   @Override
   public void exitSeikg_ike_policy(Seikg_ike_policyContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentIkeGateway.setIkePolicy(name);
      _currentIkeGateway.setIkePolicyLine(line);
   }

   @Override
   public void exitSeikg_local_address(Seikg_local_addressContext ctx) {
      Ip ip = new Ip(ctx.IP_ADDRESS().getText());
      _currentIkeGateway.setLocalAddress(ip);
   }

   @Override
   public void exitSeikp_pre_shared_key(Seikp_pre_shared_keyContext ctx) {
      String key = unquote(ctx.key.getText());
      String decodedKeyHash = JuniperUtils
            .decryptAndHashJuniper9CipherText(key);
      _currentIkePolicy.setPreSharedKeyHash(decodedKeyHash);
   }

   @Override
   public void exitSeikp_proposal_set(Seikp_proposal_setContext ctx) {
      Set<String> proposalsInSet = initIkeProposalSet(ctx.proposal_set_type());
      for (String proposal : proposalsInSet) {
         _currentIkePolicy.getProposals().put(proposal, -1);
      }
   }

   @Override
   public void exitSeikp_proposals(Seikp_proposalsContext ctx) {
      String proposal = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentIkePolicy.getProposals().put(proposal, line);
   }

   @Override
   public void exitSeikpr_authentication_algorithm(
         Seikpr_authentication_algorithmContext ctx) {
      IkeAuthenticationAlgorithm alg = toIkeAuthenticationAlgorithm(
            ctx.ike_authentication_algorithm());
      _currentIkeProposal.setAuthenticationAlgorithm(alg);
   }

   @Override
   public void exitSeikpr_authentication_method(
         Seikpr_authentication_methodContext ctx) {
      IkeAuthenticationMethod authenticationMethod = toIkeAuthenticationMethod(
            ctx.ike_authentication_method());
      _currentIkeProposal.setAuthenticationMethod(authenticationMethod);
   }

   @Override
   public void exitSeikpr_dh_group(Seikpr_dh_groupContext ctx) {
      DiffieHellmanGroup group = toDhGroup(ctx.dh_group());
      _currentIkeProposal.setDiffieHellmanGroup(group);
   }

   @Override
   public void exitSeikpr_encryption_algorithm(
         Seikpr_encryption_algorithmContext ctx) {
      EncryptionAlgorithm alg = toEncryptionAlgorithm(
            ctx.encryption_algorithm());
      _currentIkeProposal.setEncryptionAlgorithm(alg);
   }

   @Override
   public void exitSeikpr_lifetime_seconds(Seikpr_lifetime_secondsContext ctx) {
      int lifetimeSeconds = toInt(ctx.seconds);
      _currentIkeProposal.setLifetimeSeconds(lifetimeSeconds);
   }

   @Override
   public void exitSeip_policy(Seip_policyContext ctx) {
      _currentIpsecPolicy = null;
   }

   @Override
   public void exitSeip_proposal(Seip_proposalContext ctx) {
      _currentIpsecProposal = null;
   }

   @Override
   public void exitSeip_vpn(Seip_vpnContext ctx) {
      _currentIpsecVpn = null;
   }

   @Override
   public void exitSeipp_perfect_forward_secrecy(
         Seipp_perfect_forward_secrecyContext ctx) {
      DiffieHellmanGroup dhGroup = toDhGroup(ctx.dh_group());
      _currentIpsecPolicy.setPfsKeyGroup(dhGroup);
   }

   @Override
   public void exitSeipp_proposal_set(Seipp_proposal_setContext ctx) {
      Set<String> proposalsInSet = initIpsecProposalSet(
            ctx.proposal_set_type());
      for (String proposal : proposalsInSet) {
         _currentIpsecPolicy.getProposals().put(proposal, -1);
      }
   }

   @Override
   public void exitSeipp_proposals(Seipp_proposalsContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentIpsecPolicy.getProposals().put(name, line);
   }

   @Override
   public void exitSeippr_authentication_algorithm(
         Seippr_authentication_algorithmContext ctx) {
      IpsecAuthenticationAlgorithm alg = toIpsecAuthenticationAlgorithm(
            ctx.ipsec_authentication_algorithm());
      _currentIpsecProposal.setAuthenticationAlgorithm(alg);
   }

   @Override
   public void exitSeippr_encryption_algorithm(
         Seippr_encryption_algorithmContext ctx) {
      EncryptionAlgorithm alg = toEncryptionAlgorithm(
            ctx.encryption_algorithm());
      _currentIpsecProposal.setEncryptionAlgorithm(alg);
   }

   @Override
   public void exitSeippr_lifetime_kilobytes(
         Seippr_lifetime_kilobytesContext ctx) {
      int lifetimeKilobytes = toInt(ctx.kilobytes);
      _currentIpsecProposal.setLifetimeKilobytes(lifetimeKilobytes);
   }

   @Override
   public void exitSeippr_lifetime_seconds(Seippr_lifetime_secondsContext ctx) {
      int lifetimeSeconds = toInt(ctx.seconds);
      _currentIpsecProposal.setLifetimeSeconds(lifetimeSeconds);
   }

   @Override
   public void exitSeippr_protocol(Seippr_protocolContext ctx) {
      IpsecProtocol protocol = toIpsecProtocol(ctx.ipsec_protocol());
      _currentIpsecProposal.setProtocol(protocol);
   }

   @Override
   public void exitSeipv_bind_interface(Seipv_bind_interfaceContext ctx) {
      Interface iface = initInterface(ctx.interface_id());
      int line = ctx.interface_id().getStart().getLine();
      _currentIpsecVpn.setBindInterface(iface);
      _currentIpsecVpn.setBindInterfaceLine(line);
   }

   @Override
   public void exitSeipvi_gateway(Seipvi_gatewayContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentIpsecVpn.setGateway(name);
      _currentIpsecVpn.setGatewayLine(line);
   }

   @Override
   public void exitSeipvi_ipsec_policy(Seipvi_ipsec_policyContext ctx) {
      String name = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentIpsecVpn.setIpsecPolicy(name);
      _currentIpsecVpn.setIpsecPolicyLine(line);
   }

   @Override
   public void exitSep_default_policy(Sep_default_policyContext ctx) {
      if (ctx.PERMIT_ALL() != null) {
         _defaultCrossZoneAction = LineAction.ACCEPT;
      }
      else if (ctx.DENY_ALL() != null) {
         _defaultCrossZoneAction = LineAction.REJECT;
      }
   }

   @Override
   public void exitSep_from_zone(Sep_from_zoneContext ctx) {
      _currentFilter = null;
   }

   @Override
   public void exitSepf_policy(Sepf_policyContext ctx) {
      _currentFwTerm = null;
   }

   @Override
   public void exitSepfpm_application(Sepfpm_applicationContext ctx) {
      if (ctx.ANY() != null) {
         return;
      }
      else if (ctx.junos_application() != null) {
         Application application = toApplication(ctx.junos_application());
         if (application.getIpv6()) {
            _currentFwTerm.setIpv6(true);
         }
         else {
            FwFromApplication from = new FwFromApplication(application);
            _currentFwTerm.getFromApplications().add(from);
         }
      }
      else {
         String name = ctx.name.getText();
         FwFromApplication from = new FwFromApplication(
               name,
               _configuration.getApplications());
         _currentFwTerm.getFromApplications().add(from);
      }
   }

   @Override
   public void exitSepfpm_destination_address(
         Sepfpm_destination_addressContext ctx) {
      if (ctx.address_specifier().ANY() != null) {
         return;
      }
      else if (ctx.address_specifier().ANY_IPV4() != null) {
         return;
      }
      else if (ctx.address_specifier().ANY_IPV6() != null) {
         _currentFwTerm.setIpv6(true);
         return;
      }
      else if (ctx.address_specifier().variable() != null) {
         String addressBookEntryName = ctx.address_specifier().variable()
               .getText();
         FwFrom match = new FwFromDestinationAddressBookEntry(
               _currentToZone.getAddressBook(), addressBookEntryName);
         _currentFwTerm.getFroms().add(match);
      }
      else {
         throw new BatfishException("Invalid address-specifier");
      }
   }

   @Override
   public void exitSepfpm_source_address(Sepfpm_source_addressContext ctx) {
      if (ctx.address_specifier().ANY() != null) {
         return;
      }
      else if (ctx.address_specifier().ANY_IPV4() != null) {
         return;
      }
      else if (ctx.address_specifier().ANY_IPV6() != null) {
         _currentFwTerm.setIpv6(true);
         return;
      }
      else if (ctx.address_specifier().variable() != null) {
         String addressBookEntryName = ctx.address_specifier().variable()
               .getText();
         FwFrom match = new FwFromSourceAddressBookEntry(
               _currentFromZone.getAddressBook(), addressBookEntryName);
         _currentFwTerm.getFroms().add(match);
      }
      else {
         throw new BatfishException("Invalid address-specifier");
      }
   }

   @Override
   public void exitSepfpm_source_identity(Sepfpm_source_identityContext ctx) {
      if (ctx.ANY() != null) {
         return;
      }
      else {
         throw new UnsupportedOperationException(
               "no implementation for generated method");
         // TODO Auto-generated method stub
      }
   }

   @Override
   public void exitSepfpt_deny(Sepfpt_denyContext ctx) {
      _currentFwTerm.getThens().add(FwThenDiscard.INSTANCE);
   }

   @Override
   public void exitSepfpt_permit(Sepfpt_permitContext ctx) {
      if (ctx.sepfptp_tunnel() != null) {
         // Used for dynamic VPNs (no bind interface tied to a zone)
         // TODO: change from deny to appropriate action when implemented
         todo(ctx, F_PERMIT_TUNNEL);
         _currentFwTerm.getThens().add(FwThenDiscard.INSTANCE);
      }
      else {
         _currentFwTerm.getThens().add(FwThenAccept.INSTANCE);
      }
   }

   @Override
   public void exitSez_security_zone(Sez_security_zoneContext ctx) {
      _currentZone = null;
      _currentZoneInboundFilter = null;
   }

   @Override
   public void exitSezs_address_book(Sezs_address_bookContext ctx) {
      _currentAddressBook = null;
   }

   @Override
   public void exitSezs_interfaces(Sezs_interfacesContext ctx) {
      _currentZoneInterface = null;
   }

   @Override
   public void exitSezsa_address(Sezsa_addressContext ctx) {
      String name = ctx.name.getText();
      Prefix prefix = new Prefix(ctx.IP_PREFIX().getText());
      AddressBookEntry addressEntry = new AddressAddressBookEntry(name, prefix);
      _currentZone.getAddressBook().getEntries().put(name, addressEntry);
   }

   @Override
   public void exitSezsa_address_set(Sezsa_address_setContext ctx) {
      _currentAddressSetAddressBookEntry = null;
   }

   @Override
   public void exitSezsaa_address(Sezsaa_addressContext ctx) {
      String name = ctx.name.getText();
      _currentAddressSetAddressBookEntry.getEntries()
            .add(new AddressSetEntry(name, _currentAddressBook));
   }

   @Override
   public void exitSezsaa_address_set(Sezsaa_address_setContext ctx) {
      String name = ctx.name.getText();
      _currentAddressSetAddressBookEntry.getEntries()
            .add(new AddressSetEntry(name, _currentAddressBook));
   }

   @Override
   public void exitSezsh_protocols(Sezsh_protocolsContext ctx) {
      HostProtocol protocol = toHostProtocol(ctx.hib_protocol());
      String termName = protocol.name();
      FwTerm newTerm = new FwTerm(termName);
      _currentZoneInboundFilter.getTerms().put(termName, newTerm);
      newTerm.getFromHostProtocols().add(new FwFromHostProtocol(protocol));
      newTerm.getThens().add(FwThenAccept.INSTANCE);
   }

   @Override
   public void exitSezsh_system_services(Sezsh_system_servicesContext ctx) {
      HostSystemService service = toHostSystemService(ctx.hib_system_service());
      String termName = service.name();
      FwTerm newTerm = new FwTerm(termName);
      _currentZoneInboundFilter.getTerms().put(termName, newTerm);
      newTerm.getFromHostServices().add(new FwFromHostService(service));
      newTerm.getThens().add(FwThenAccept.INSTANCE);
   }

   @Override
   public void exitSnmp_community(Snmp_communityContext ctx) {
      _currentSnmpCommunity = null;
   }

   @Override
   public void exitSnmpc_authorization(Snmpc_authorizationContext ctx) {
      if (ctx.READ_ONLY() != null) {
         _currentSnmpCommunity.setRo(true);
      }
      else if (ctx.READ_WRITE() != null) {
         _currentSnmpCommunity.setRw(true);
      }
      else {
         throw new BatfishException("Invalid authorization");
      }
   }

   @Override
   public void exitSnmpc_client_list_name(Snmpc_client_list_nameContext ctx) {
      String list = ctx.name.getText();
      int line = ctx.name.getStart().getLine();
      _currentSnmpCommunity.setAccessList(list);
      _currentSnmpCommunity.setAccessListLine(line);
      // TODO: verify whether both ipv4 and ipv6 list should be set with this
      // command
      _currentSnmpCommunity.setAccessList6(list);
      _currentSnmpCommunity.setAccessList6Line(line);
   }

   @Override
   public void exitSnmptg_targets(Snmptg_targetsContext ctx) {
      Ip ip = new Ip(ctx.target.getText());
      String name = ip.toString();
      SnmpHost host = _currentSnmpServer.getHosts().get(name);
      if (host == null) {
         host = new SnmpHost(ip.toString());
         _currentSnmpServer.getHosts().put(name, host);
      }
   }

   @Override
   public void exitSy_default_address_selection(
         Sy_default_address_selectionContext ctx) {
      _configuration.setDefaultAddressSelection(true);
   }

   @Override
   public void exitSy_domain_name(Sy_domain_nameContext ctx) {
      String domainName = ctx.variable().getText();
      _currentRoutingInstance.setDomainName(domainName);
   }

   @Override
   public void exitSy_host_name(Sy_host_nameContext ctx) {
      String hostname = ctx.variable().getText();
      _currentRoutingInstance.setHostname(hostname);
   }

   @Override
   public void exitSy_name_server(Sy_name_serverContext ctx) {
      Set<String> dnsServers = _configuration.getDnsServers();
      String hostname = ctx.hostname.getText();
      dnsServers.add(hostname);
   }

   public JuniperConfiguration getConfiguration() {
      return _configuration;
   }

   private String getInterfaceName(Interface_idContext ctx) {
      String name = ctx.name.getText();
      if (ctx.suffix != null) {
         name += ":" + ctx.suffix.getText();
      }
      return name;
   }

   private String initIkeProposal(IkeProposal proposal) {
      String name = proposal.getName();
      _configuration.getIkeProposals().put(name, proposal);
      return name;
   }

   private Set<String> initIkeProposalSet(Proposal_set_typeContext ctx) {
      Set<String> proposals = new HashSet<>();
      if (ctx.BASIC() != null) {
         proposals.add(initIkeProposal(IkeProposal.PSK_DES_DH1_SHA1));
         proposals.add(initIkeProposal(IkeProposal.PSK_DES_DH1_MD5));
      }
      else if (ctx.COMPATIBLE() != null) {
         proposals.add(initIkeProposal(IkeProposal.PSK_3DES_DH2_MD5));
         proposals.add(initIkeProposal(IkeProposal.PSK_DES_DH2_SHA1));
         proposals.add(initIkeProposal(IkeProposal.PSK_DES_DH2_MD5));
      }
      else if (ctx.STANDARD() != null) {
         proposals.add(initIkeProposal(IkeProposal.PSK_3DES_DH2_SHA1));
         proposals.add(initIkeProposal(IkeProposal.PSK_AES128_DH2_SHA1));
      }
      return proposals;
   }

   private Interface initInterface(Interface_idContext id) {
      String currentRoutingInstance = _currentRoutingInstance.getName();
      Map<String, Interface> interfaces;
      if (id.node != null) {
         String nodeDeviceName = id.node.getText();
         NodeDevice nodeDevice = _configuration.getNodeDevices()
               .get(nodeDeviceName);
         if (nodeDevice == null) {
            nodeDevice = new NodeDevice(nodeDeviceName);
            _configuration.getNodeDevices().put(nodeDeviceName, nodeDevice);
         }
         interfaces = nodeDevice.getInterfaces();
      }
      else {
         interfaces = _configuration.getInterfaces();
      }
      String name = id.name.getText();
      int definitionLine = id.name.getLine();
      String unit = null;
      if (id.unit != null) {
         unit = id.unit.getText();
         definitionLine = id.unit.getLine();
      }
      String unitFullName = name + "." + unit;
      Interface iface = interfaces.get(name);
      if (iface == null) {
         iface = new Interface(name, definitionLine);
         iface.setRoutingInstance(currentRoutingInstance);
         interfaces.put(name, iface);
      }
      if (unit != null) {
         Map<String, Interface> units = iface.getUnits();
         iface = units.get(unitFullName);
         if (iface == null) {
            iface = new Interface(unitFullName, definitionLine);
            iface.setRoutingInstance(currentRoutingInstance);
            units.put(unitFullName, iface);
         }
      }
      return iface;
   }

   private String initIpsecProposal(IpsecProposal proposal) {
      String name = proposal.getName();
      _configuration.getIpsecProposals().put(name, proposal);
      return name;
   }

   private Set<String> initIpsecProposalSet(Proposal_set_typeContext ctx) {
      Set<String> proposals = new HashSet<>();
      if (ctx.BASIC() != null) {
         proposals.add(initIpsecProposal(IpsecProposal.NOPFS_ESP_DES_SHA));
         proposals.add(initIpsecProposal(IpsecProposal.NOPFS_ESP_DES_MD5));
      }
      else if (ctx.COMPATIBLE() != null) {
         proposals.add(initIpsecProposal(IpsecProposal.NOPFS_ESP_3DES_SHA));
         proposals.add(initIpsecProposal(IpsecProposal.NOPFS_ESP_3DES_MD5));
         proposals.add(initIpsecProposal(IpsecProposal.NOPFS_ESP_DES_SHA));
         proposals.add(initIpsecProposal(IpsecProposal.NOPFS_ESP_DES_MD5));
      }
      else if (ctx.STANDARD() != null) {
         proposals.add(initIpsecProposal(IpsecProposal.G2_ESP_3DES_SHA));
         proposals.add(initIpsecProposal(IpsecProposal.G2_ESP_AES128_SHA));
      }
      return proposals;
   }

   private AsPath toAsPath(As_path_exprContext path) {
      List<SortedSet<Integer>> asPath = new ArrayList<>();
      for (As_unitContext ctx : path.items) {
         SortedSet<Integer> asSet = new TreeSet<>();
         if (ctx.DEC() != null) {
            asSet.add(toInt(ctx.DEC()));
         }
         else {
            for (Token token : ctx.as_set().items) {
               asSet.add(toInt(token));
            }
         }
         asPath.add(asSet);
      }
      return new AsPath(asPath);
   }

   private long toCommunityLong(Ec_literalContext ctx) {
      String[] parts = ctx.getText().split(":");
      int part1 = Integer.parseInt(parts[0]);
      long part2 = Long.parseLong(parts[1]);
      int part3 = Integer.parseInt(parts[2]);
      ExtendedCommunity c = new ExtendedCommunity(part1, part2, part3);
      return c.asLong();
   }

   private long toCommunityLong(Ec_namedContext ctx) {
      ExtendedCommunity ec = new ExtendedCommunity(ctx.getText());
      return ec.asLong();
   }

   private long toCommunityLong(Extended_communityContext ctx) {
      if (ctx.ec_literal() != null) {
         return toCommunityLong(ctx.ec_literal());
      }
      else if (ctx.ec_named() != null) {
         return toCommunityLong(ctx.ec_named());
      }
      else {
         throw new BatfishException("invalid extended community");
      }
   }

   private String toComplexPolicyStatement(Policy_expressionContext expr) {
      if (expr.pe_nested() != null) {
         return toComplexPolicyStatement(expr.pe_nested().policy_expression());
      }
      else if (expr.variable() != null) {
         String name = expr.variable().getText();
         return name;
      }
      else if (expr.pe_conjunction() != null) {
         Set<String> conjuncts = new LinkedHashSet<>();
         for (Policy_expressionContext conjunctCtx : expr.pe_conjunction()
               .policy_expression()) {
            String conjunctName = toComplexPolicyStatement(conjunctCtx);
            conjuncts.add(conjunctName);
         }
         String conjunctionPolicyName = "~CONJUNCTION_POLICY_"
               + _conjunctionPolicyIndex + "~";
         _conjunctionPolicyIndex++;
         PolicyStatement conjunctionPolicy = new PolicyStatement(
               conjunctionPolicyName, -1);
         PsTerm conjunctionPolicyTerm = conjunctionPolicy.getDefaultTerm();
         PsFromPolicyStatementConjunction from = new PsFromPolicyStatementConjunction(
               conjuncts);
         conjunctionPolicyTerm.getFroms().add(from);
         conjunctionPolicyTerm.getThens().add(PsThenAccept.INSTANCE);
         _configuration.getPolicyStatements().put(
               conjunctionPolicyName,
               conjunctionPolicy);
         return conjunctionPolicyName;
      }
      else if (expr.pe_disjunction() != null) {
         Set<String> disjuncts = new LinkedHashSet<>();
         for (Policy_expressionContext disjunctCtx : expr.pe_disjunction()
               .policy_expression()) {
            String disjunctName = toComplexPolicyStatement(disjunctCtx);
            disjuncts.add(disjunctName);
         }
         String disjunctionPolicyName = "~DISJUNCTION_POLICY_"
               + _disjunctionPolicyIndex + "~";
         _disjunctionPolicyIndex++;
         PolicyStatement disjunctionPolicy = new PolicyStatement(
               disjunctionPolicyName, -1);
         PsTerm disjunctionPolicyTerm = disjunctionPolicy.getDefaultTerm();
         for (String disjunct : disjuncts) {
            PsFromPolicyStatement from = new PsFromPolicyStatement(disjunct);
            disjunctionPolicyTerm.getFroms().add(from);
         }
         disjunctionPolicyTerm.getThens().add(PsThenAccept.INSTANCE);
         _configuration.getPolicyStatements().put(
               disjunctionPolicyName,
               disjunctionPolicy);
         return disjunctionPolicyName;
      }
      else {
         throw new BatfishException("Invalid policy expression");
      }
   }

   private DiffieHellmanGroup toDhGroup(Dh_groupContext ctx) {
      if (ctx.GROUP1() != null) {
         return DiffieHellmanGroup.GROUP1;
      }
      else if (ctx.GROUP14() != null) {
         return DiffieHellmanGroup.GROUP14;
      }
      else if (ctx.GROUP2() != null) {
         return DiffieHellmanGroup.GROUP2;
      }
      else if (ctx.GROUP5() != null) {
         return DiffieHellmanGroup.GROUP5;
      }
      else {
         throw new BatfishException("invalid dh-group");
      }
   }

   private void todo(ParserRuleContext ctx, String feature) {
      _w.todo(ctx, feature, _parser, _text);
      _unimplementedFeatures.add("Juniper: " + feature);
   }

}

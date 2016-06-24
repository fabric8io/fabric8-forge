/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.camel.tooling.util;

import io.fabric8.utils.Strings;
import org.apache.camel.ExchangePattern;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.InOnlyDefinition;
import org.apache.camel.model.InOutDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RemovePropertyDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.model.SetExchangePatternDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.WhenDefinition;

import java.beans.Introspector;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;


public class CamelModelHelper {

    public static String getUri(FromDefinition input) {
        String key = input.getUri();
        if (Strings2.isEmpty(key)) {
            String ref = input.getRef();
            if (!Strings2.isEmpty(ref)) {
                return "ref:" + ref;
            }
        }
        return key;
    }

    public static String getUri(ToDefinition input) {
        String key = input.getUri();
        if (Strings2.isEmpty(key)) {
            String ref = input.getRef();
            if (!Strings2.isEmpty(ref)) {
                return "ref:" + ref;
            }
        }
        return key;
    }

    public static String getExchangePattern(ToDefinition input) {
        String pattern = input.getPattern() != null ? input.getPattern().name() : null;
        if (Strings2.isEmpty(pattern)) {
            return null;
        }
        return pattern;
    }

    public static boolean isMockEndpointURI(String value) {
        return value.startsWith("mock:");
    }

    public static boolean isTimerEndpointURI(String value) {
        return value.startsWith("timer:") || value.startsWith("quartz:");
    }

    /**
     * Returns the summary label of a node for visualisation purposes
     */
    public static String getDisplayText(OptionalIdentifiedDefinition camelNode) {
        String id = camelNode.getId();
        if (!Strings2.isEmpty(id)) {
            return id;
        }
        if (camelNode instanceof FromDefinition) {
            FromDefinition node = (FromDefinition) camelNode;
            return getUri(node);
        } else if (camelNode instanceof ToDefinition) {
            ToDefinition node = (ToDefinition) camelNode;
            return getUri(node);
        } else if (camelNode instanceof BeanDefinition) {
            BeanDefinition node = (BeanDefinition) camelNode;
            return "bean " + getOrBlank(node.getRef());
        } else if (camelNode instanceof CatchDefinition) {
            CatchDefinition node = (CatchDefinition) camelNode;
            List exceptions = node.getExceptions();
            if (exceptions != null && exceptions.size() > 0) {
                return "catch " + exceptions;
            } else {
                return "catch " + Expressions.getExpressionOrElse(node.getHandled());
            }
        } else if (camelNode instanceof ChoiceDefinition) {
            return "choice";
        } else if (camelNode instanceof ConvertBodyDefinition) {
            ConvertBodyDefinition node = (ConvertBodyDefinition) camelNode;
            return "convertBodyTo " + getOrBlank(node.getType());
        } else if (camelNode instanceof EnrichDefinition) {
            EnrichDefinition node = (EnrichDefinition) camelNode;
            //return "enrich " + getOrBlank(node.getResourceUri());
            return "enrich " + Expressions.getExpressionOrElse(node.getExpression());
        } else if (camelNode instanceof FinallyDefinition) {
            return "finally";
        } else if (camelNode instanceof InOnlyDefinition) {
            InOnlyDefinition node = (InOnlyDefinition) camelNode;
            return "inOnly " + getOrBlank(node.getUri());
        } else if (camelNode instanceof InOutDefinition) {
            InOutDefinition node = (InOutDefinition) camelNode;
            return "inOut " + getOrBlank(node.getUri());
        } else if (camelNode instanceof InterceptSendToEndpointDefinition) {
            InterceptSendToEndpointDefinition node = (InterceptSendToEndpointDefinition) camelNode;
            return "intercept " + getOrBlank(node.getUri());
        } else if (camelNode instanceof LogDefinition) {
            LogDefinition node = (LogDefinition) camelNode;
            return "log " + getOrBlank(node.getLogName());
        } else if (camelNode instanceof MarshalDefinition) {
            return "marshal";
        } else if (camelNode instanceof OnExceptionDefinition) {
            OnExceptionDefinition node = (OnExceptionDefinition) camelNode;
            return "on exception " + getOrBlank(node.getExceptions());
        } else if (camelNode instanceof OtherwiseDefinition) {
            return "otherwise";
        } else if (camelNode instanceof PollEnrichDefinition) {
            PollEnrichDefinition node = (PollEnrichDefinition) camelNode;
            // TODO
            // return "poll enrich " + getOrBlank(node.getResourceUri());
            return "poll enrich " + Expressions.getExpressionOrElse(node.getExpression());
        } else if (camelNode instanceof RemoveHeaderDefinition) {
            RemoveHeaderDefinition node = (RemoveHeaderDefinition) camelNode;
            return "remove header " + getOrBlank(node.getHeaderName());
        } else if (camelNode instanceof RemovePropertyDefinition) {
            RemovePropertyDefinition node = (RemovePropertyDefinition) camelNode;
            return "remove property " + getOrBlank(node.getPropertyName());
        } else if (camelNode instanceof RollbackDefinition) {
            RollbackDefinition node = (RollbackDefinition) camelNode;
            return "rollback " + getOrBlank(node.getMessage());
        } else if (camelNode instanceof SetExchangePatternDefinition) {
            SetExchangePatternDefinition node = (SetExchangePatternDefinition) camelNode;
            ExchangePattern pattern = node.getPattern();
            if (pattern == null) {
                return "setExchangePattern";
            } else {
                return "set " + pattern;
            }
        } else if (camelNode instanceof SortDefinition) {
            SortDefinition node = (SortDefinition) camelNode;
            return "sort " + Expressions.getExpressionOrElse(node.getExpression());
        } else if (camelNode instanceof WhenDefinition) {
            WhenDefinition node = (WhenDefinition) camelNode;
            return "when " + Expressions.getExpressionOrElse(node.getExpression());
        } else if (camelNode instanceof UnmarshalDefinition) {
            return "unmarshal";
        } else if (camelNode instanceof TryDefinition) {
            return "try";
        } else if (camelNode instanceof LoadBalanceDefinition) {
            LoadBalanceDefinition load = (LoadBalanceDefinition) camelNode;
            return load.getShortName();
/*
            TODO

   			if (load.getRef() != null) {
   				return "custom " + getOrBlank(load.getRef());
   			} else if (load.getLoadBalancerType() != null) {
   				if (load.getLoadBalancerType().getClass().isAssignableFrom(CustomLoadBalancerDefinition.class)) {
   					CustomLoadBalancerDefinition custom = (CustomLoadBalancerDefinition) load.getLoadBalancerType();
   					return "custom " + getOrBlank(custom.getRef());
   				} else if (load.getLoadBalancerType().getClass().isAssignableFrom(FailoverLoadBalancerDefinition.class)) {
   					return "failover";
   				} else if (load.getLoadBalancerType().getClass().isAssignableFrom(RandomLoadBalancerDefinition.class)) {
   					return "random";
   				} else if (load.getLoadBalancerType().getClass().isAssignableFrom(RoundRobinLoadBalancerDefinition.class)) {
   					return "round robin";
   				} else if (load.getLoadBalancerType().getClass().isAssignableFrom(StickyLoadBalancerDefinition.class)) {
   					return "sticky";
   				} else if (load.getLoadBalancerType().getClass().isAssignableFrom(TopicLoadBalancerDefinition.class)) {
   					return "topic";
   				} else if (load.getLoadBalancerType().getClass().isAssignableFrom(WeightedLoadBalancerDefinition.class)) {
   					return "weighted";
   				}
   			} else {
   				return "load balance";
   			}
*/
        }

        String answer = null;
        try {
            answer = camelNode.getLabel();
        } catch (Exception e) {
            // ignore errors in Camel
        }
        if (Strings2.isBlank(answer)) {
            answer = getPatternName(camelNode);
        }
        return answer;
    }

    private static String getOrBlank(List<String> values) {
        if (values != null && values.size() > 0) {
            return Strings.join(values, ", ");
        }
        return "";
    }

    public static String getOrBlank(String text) {
        return Strings2.isEmpty(text) ? "" : text;
    }

    /**
     * Returns the pattern name
     */
    public static String getPatternName(OptionalIdentifiedDefinition camelNode) {
        // we should grab the annotation instead
        XmlRootElement root = camelNode.getClass().getAnnotation(XmlRootElement.class);
        if (root != null) {
            return root.name();
        }
        String simpleName = Strings.stripSuffix(camelNode.getClass().getSimpleName(), "Definition");
        return Introspector.decapitalize(simpleName);
    }

    public static String getDescription(OptionalIdentifiedDefinition definition) {
        return definition.getDescriptionText();
    }

    /**
     * Returns the text to display as the tooltip of a shape figure.
     *
     * @return the tooltip to display for the shape figure
     */
/*   	public static String getDisplayToolTip(ProcessorDefinition camelDef) {
           if (camelDef instanceof WhenDefinition) {
            WhenDefinitionDefinition node = (WhenDefinition) camelDef;
   			return "when " + Expressions.getExpressionOrElse(node.getExpression());
   		}
        // TODO
   		String answer = null;
   		// String answer = Tooltips.tooltip(getPatternName());
   		if (answer == null) {
   			ProcessorDefinition camelDef = createCamelDefinition();
   			if (camelDef != null) {
   				if (camelDef instanceof RouteDefinition) {
   					RouteDefinition route = (RouteDefinition) camelDef;
   					return "Route " + (route.getId() != null ? route.getId() : "");
   				} else if (camelDef instanceof ToDefinition && isNotTarget()) {
   					// if its the first in the route and its an endpoint, then use From instead of To
   					// (notice that createCamelDefinition returns a ToDefinition for all kind of Endpoints)
   					return "From " + camelDef.getLabel();
   				}
   				return camelDef.getShortName() + " " + camelDef.getLabel();
   			}
   			return getDescription();
   		}
   		return answer;
   	}*/
}

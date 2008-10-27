package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.Configuration;
import org.jetbrains.idea.maven.dom.model.ConfigurationParameter;
import org.jetbrains.idea.maven.dom.model.PluginExecution;
import org.jetbrains.idea.maven.dom.plugin.MavenPluginModel;
import org.jetbrains.idea.maven.dom.plugin.Mojo;
import org.jetbrains.idea.maven.dom.plugin.Parameter;

import java.util.*;

public class MavenPluginConfigurationDomExtender extends DomExtender<Configuration> {
  public static final Key<Parameter> PLUGIN_PARAMETER_KEY = Key.create("MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY");

  @Override
  public void registerExtensions(@NotNull Configuration config, @NotNull DomExtensionsRegistrar r) {
    MavenPluginModel pluginModel = MavenPluginDomUtil.getMavenPlugin(config);
    if (pluginModel == null) {
      r.registerCustomChildrenExtension(ConfigurationParameter.class);
      return;
    }

    for (Parameter each : collectParameters(pluginModel, config)) {
      registerPluginParameter(r, each);
    }
  }

  private Collection<Parameter> collectParameters(MavenPluginModel pluginModel, Configuration config) {
    List<String> selectedGoals = null;

    PluginExecution executionElement = config.getParentOfType(PluginExecution.class, false);
    if (executionElement != null) {
      selectedGoals = new ArrayList<String>();
      for (GenericDomValue<String> goal : executionElement.getGoals().getGoals()) {
        selectedGoals.add(goal.getStringValue());
      }
    }

    Map<String, Parameter> namesWithParameters = new HashMap<String, Parameter>();

    for (Mojo eachMojo : pluginModel.getMojos().getMojos()) {
      String goal = eachMojo.getGoal().getStringValue();
      if (goal == null) continue;

      if (selectedGoals == null || selectedGoals.contains(goal)) {
        for (Parameter eachParameter : eachMojo.getParameters().getParameters()) {
          if (!eachParameter.getEditable().getValue()) continue;

          String name = eachParameter.getName().getStringValue();
          if (name == null) continue;

          if (namesWithParameters.containsKey(name)) continue;
          namesWithParameters.put(name, eachParameter);
        }
      }
    }

    return namesWithParameters.values();
  }

  private void registerPluginParameter(DomExtensionsRegistrar r, final Parameter parameter) {
    String paramName = parameter.getName().getStringValue();
    String alias = parameter.getAlias().getStringValue();

    registerPluginParameter(r, parameter, paramName);
    if (alias != null) registerPluginParameter(r, parameter, alias);
  }

  private void registerPluginParameter(DomExtensionsRegistrar r, final Parameter parameter, final String parameterName) {
    DomExtension e;
    if (isCollection(parameter)) {
      e = r.registerFixedNumberChildExtension(new XmlName(parameterName), ConfigurationParameter.class);
      e.addExtender(new DomExtender() {
        public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
          for (String each : collectPossibleNameForCollectionParameter(parameterName)) {
            DomExtension inner = registrar.registerCollectionChildrenExtension(new XmlName(each), ConfigurationParameter.class);
            inner.putUserData(DomExtension.KEY_DECLARATION, parameter);
          }
        }
      });
    }
    else {
      e = r.registerFixedNumberChildExtension(new XmlName(parameterName), ConfigurationParameter.class);
    }
    e.putUserData(DomExtension.KEY_DECLARATION, parameter);

    parameter.getXmlElement().putUserData(PLUGIN_PARAMETER_KEY, parameter);
  }

  public List<String> collectPossibleNameForCollectionParameter(String parameterName) {
    String singularName = StringUtil.unpluralize(parameterName);
    if (singularName == null) singularName = parameterName;

    List<String> result = new ArrayList<String>();
    String[] parts = NameUtil.splitNameIntoWords(singularName);
    for (int i = 0; i < parts.length; i++) {
      result.add(StringUtil.decapitalize(StringUtil.join(parts, i, parts.length, "")));
    }
    return result;
  }

  private boolean isCollection(Parameter parameter) {
    String type = parameter.getType().getStringValue();
    if (type.endsWith("[]")) return true;

    List<String> collectionClasses = Arrays.asList("java.util.List",
                                                   "java.util.Set",
                                                   "java.util.Collection");
    return collectionClasses.contains(type);
  }
}
/*
 * Copyright (C) 2015 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.kubernetes.workflow;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class Item extends AbstractDescribableImpl<Item> {

    private static final String VALUE = "value";

    private String value;

    @DataBoundConstructor
    public Item( String value) {
        this.value = value;
    }


    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "{value:'" + value + '\'' + '}';
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Item> {
        @Override
        public String getDisplayName() {
            return "Items";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            return super.configure(req, json);
        }

        @Override
        public Item newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new Item(formData.get(VALUE).toString());
        }
    }
}
#! /bin/sh
######################################################################
#
# Run the Cloudnet TOSCA toolbox.
#
# Copyright (c) 2021 Orange
#
# Author(s):
# - Philippe Merle <philippe.merle@inria.fr>
#
#    Licensed under the Apache License, Version 2.0 (the "License"); you may
#    not use this file except in compliance with the License. You may obtain
#    a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#    License for the specific language governing permissions and limitations
#    under the License.
#
######################################################################

# Check that the CLOUDNET_BINDIR environment variable is set correctly.
if [ ! -f ${CLOUDNET_BINDIR}/cloudnet_rc.sh ]
then
  echo "The CLOUDNET_BINDIR environment variable is not set correctly!"
  exit 1
fi

# Load cloudnet commands.
. ${CLOUDNET_BINDIR}/cloudnet_rc.sh

echo ------------------
echo Create .zip files
echo ------------------

zipper()
{
  echo Creating $1.zip...
  cd $1
  rm -f ../$1.zip .DS_Store
  zip -r ../$1.zip .
  cd ..
}

zipper csar_sockshop_dep
zipper csar_wp_container
zipper csar_wp_container_heavy
zipper csar_wp_vm

echo -------------------
echo Process .zip files
echo -------------------

translate csar_sockshop_dep.zip
translate csar_wp_container.zip
translate csar_wp_container_heavy.zip
translate csar_wp_vm.zip

echo ------------------------------
echo Process TORCH folder examples
echo ------------------------------

translate csar_sockshop_dep/Definitions/*.yaml
translate csar_wp_container/Definitions/*.yaml
translate csar_wp_container_heavy/Definitions/*.yaml
translate csar_wp_vm/Definitions/*.yaml

# Parse and type check all generated Alloy specifications.
alloy_parse Cloudnet_TOSCA_Toolbox_Results/Alloy/*.als

# Analyse generated Alloy specifications.
#TODO: alloy_execute Cloudnet_TOSCA_Toolbox_Results/Alloy/*.als

# Generate TOSCA diagrams.
generate_tosca_diagrams Cloudnet_TOSCA_Toolbox_Results/ToscaDiagrams/*/*.dot Cloudnet_TOSCA_Toolbox_Results/ToscaDiagrams/*.dot

# Generate network diagrams.
generate_network_diagrams Cloudnet_TOSCA_Toolbox_Results/NetworkDiagrams/*/*.nwdiag Cloudnet_TOSCA_Toolbox_Results/NetworkDiagrams/*.nwdiag

# Generate UML2 diagrams.
cp -r icons Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams
cp -r icons Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/csar_sockshop_dep.zip
cp -r icons Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/csar_wp_container.zip
cp -r icons Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/csar_wp_container_heavy.zip
cp -r icons Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/csar_wp_vm.zip
generate_uml2_diagrams Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/*/*.plantuml Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/*.plantuml

# Removed useless generated files
rm -rf Cloudnet_TOSCA_Toolbox_Results/Alloy \
       Cloudnet_TOSCA_Toolbox_Results/DeclarativeWorkflows \
       Cloudnet_TOSCA_Toolbox_Results/ToscaDiagrams/*.dot \
       Cloudnet_TOSCA_Toolbox_Results/ToscaDiagrams/*/*.dot \
       Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/*.plantuml \
       Cloudnet_TOSCA_Toolbox_Results/Uml2Diagrams/*/*.plantuml

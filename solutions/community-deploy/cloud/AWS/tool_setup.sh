#!/bin/bash
#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#

#Install Terraform
echo -e "-------------------------[ Installing Terraform ]----------------------------------------" 
yum install -y yum-utils unzip git firewalld epel-release
yum-config-manager --add-repo https://rpm.releases.hashicorp.com/RHEL/hashicorp.repo --add-repo https://download.docker.com/linux/centos/docker-ce.repo
yum -y install terraform jq docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl start docker && systemctl enable docker
sleep 30


#Install AWS CLI
echo -e "-------------------------[ Installing  AWS CLI   ]----------------------------------------" 
mkdir -p /opt/aws-cli/
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/opt/aws-cli/awscliv2.zip"
unzip "/opt/aws-cli/awscliv2.zip" -d /opt/aws-cli/
sudo /opt/aws-cli/aws/install
rm -rf /opt/aws-cli/aws

#Trigger AWS CLI configuration
echo -e "-------------------------[ AWS CLI configuration ]----------------------------------------" 
aws configure


#Initiate Terraform workspace
echo -e "------------------------[ Terraform Workspace init ]----------------------------------------" 
terraform init


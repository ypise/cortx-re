pipeline {
    agent {
        node {
            label 'mukul-community-build-multi-node'
        }
    }
    //triggers { cron('0 22 * * 1,3,5') }
    options {
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'mukul-multinode-automation', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for CORTX Cluster scripts', trim: true)
        //string(name: 'CORTX_TAG', defaultValue: 'mukul-multinode-automation', description: 'Branch or GitHash for generaing CORTX container images', trim: true)
        string(name: 'OS_VERSION', defaultValue: 'CentOS 7.9.2009 x86_64', description: 'Operating system version', trim: true)
        string(name: 'REGION', defaultValue: 'ap-south-1', description: 'AWS region', trim: true)
        string(name: 'KEY_NAME', defaultValue: 'devops-key', description: 'Key name', trim: true)
        string(name: 'COMMUNITY_USE', defaultValue: 'yes', description: 'Only use during community deployment', trim: true)
        string(name: 'VOLUME_COUNT', defaultValue: '9', description: 'EBS volume', trim: true)
        string(name: 'VOLUME_SIZE', defaultValue: '10', description: 'EBS volume size', trim: true)
        string(name: 'INSTANCE_COUNT', defaultValue: '4', description: 'Instance count', trim: true)
        string(name: 'INSTANCE_TAG_NAME', defaultValue: 'cortx-multinode', description: 'Tag name', trim: true)
        password(name: 'SECRET_KEY', description: 'secret key for AWS account')
        password(name: 'ACCESS_KEY', description: 'access key for AWS account')
        password(name: 'ROOT_PASSWORD', description: 'Root password for EC2 instances')
    }

        stages {
            stage('Checkout Script') {
                steps {
                    cleanWs()
                    script {
                        checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])
                    }
                }
            }

        stage('Install Prerequisite tools') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install prerequisite tools', script: '''
                VM_IP=$(curl ipinfo.io/ip)
                export OS_VERSION=${OS_VERSION}
                export REGION=${REGION}
                export SECRET_KEY=${SECRET_KEY}
                export ACCESS_KEY=${ACCESS_KEY}
                export KEY_NAME=${KEY_NAME}
                export VOLUME_COUNT=${VOLUME_COUNT}
                export VOLUME_SIZE=${VOLUME_SIZE}
                export INSTANCE_COUNT=${INSTANCE_COUNT}
                export INSTANCE_TAG_NAME=${INSTANCE_TAG_NAME}
                    rm -rvf /usr/local/bin/aws /usr/local/bin/aws_completer /usr/local/aws-cli >/dev/null 2>&1
                    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && yum install unzip -y && unzip awscliv2.zip
                    ./aws/install
                    aws configure set default.region $REGION; aws configure set aws_access_key_id $ACCESS_KEY; aws configure set aws_secret_access_key $SECRET_KEY
                pushd solutions/community-deploy/cloud/AWS
                    ./tool_setup.sh
                    sed -i 's,os_version          =.*,os_version          = "'"$OS_VERSION"'",g' user.tfvars && sed -i 's,region              =.*,region              = "'"$REGION"'",g' user.tfvars && sed -i 's,security_group_cidr =.*,security_group_cidr = "'"$VM_IP/32"'",g' user.tfvars && sed -i 's,instance_count          =.*,instance_count          = "'"$INSTANCE_COUNT"'",g' user.tfvars && sed -i 's,ebs_volume_count          =.*,ebs_volume_count          = "'"$VOLUME_COUNT"'",g' user.tfvars && sed -i 's,ebs_volume_size          =.*,ebs_volume_size          = "'"$VOLUME_SIZE"'",g' user.tfvars && sed -i 's,tag_name          =.*,tag_name          = "'"$INSTANCE_TAG_NAME"'",g' user.tfvars
                    echo key_name            = '"'$KEY_NAME'"' | cat >>user.tfvars
                    cat user.tfvars
                    popd
                '''
            }
        }
        stage('Create Multi EC2 instances') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up multi EC2 instances', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        terraform validate && terraform apply -var-file user.tfvars --auto-approve
                        popd
            '''
            }
        }
        stage('Configure network and storage') {
            steps {
                script { build_stage = env.STAGE_NAME }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    sh label: 'Setting up Network and Storage devices for CORTX. Script will reboot the instance on completion', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        export PUBLIC_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt | tr -d '",[]' | sed '/^$/d')
                        for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${ip}" sudo bash /home/centos/setup.sh && sleep 240;done
                        popd
            '''
                }
            }
        }
        stage('EC2 connection prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Changing root password & creating hosts file', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export ROOT_PASSWORD=${ROOT_PASSWORD}
                    export PUBLIC_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt | tr -d '",[]' | sed '/^$/d')
                    terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_dns.value 2>&1 | tee ec2_hostname.txt
                    export HOST1=$(cat ec2_hostname.txt | jq '.[0]'| tr -d '",[]')
                    export HOST2=$(cat ec2_hostname.txt | jq '.[1]'| tr -d '",[]')
                    export HOST3=$(cat ec2_hostname.txt | jq '.[2]'| tr -d '",[]')
                    export HOST4=$(cat ec2_hostname.txt | jq '.[3]'| tr -d '",[]')
                    for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${ip}" "export ROOT_PASSWORD=$ROOT_PASSWORD && echo $ROOT_PASSWORD | sudo passwd --stdin root && git clone https://github.com/Seagate/cortx-re && pushd /home/centos/cortx-re/solutions/kubernetes && touch hosts && echo hostname=${HOST1},user=root,pass= > hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && echo hostname=${HOST2},user=root,pass= >> hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && echo hostname=${HOST3},user=root,pass= >> hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && echo hostname=${HOST4},user=root,pass= >> hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && cat hosts && sleep 120";done
                    popd
            '''
            }
        }
    }
}

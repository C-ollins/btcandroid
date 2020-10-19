installGo(){
    echo "Installing golang"
    curl -O https://storage.googleapis.com/golang/go1.12.9.darwin-amd64.tar.gz
    sha256sum go1.12.9.darwin-amd64.tar.gz
    tar -xvf go1.12.9.darwin-amd64.tar.gz
    sudo chown -R root:root ./go
    sudo mv go /usr/local
}

installGomobile(){
    echo "Installing gomobile"
    go get -u golang.org/x/mobile/cmd/gomobile
    gomobile init
}

if !(hash go 2>/dev/null); then
    installGo
fi

export GOPATH=$HOME/go
export PATH=$PATH:/usr/local/go/bin:$GOPATH/bin

if !(hash gomobile 2>/dev/null); then
    installGomobile
fi

go version
echo "Building btclibwallet"
export BtcandroidDir=$(pwd)
mkdir -p $GOPATH/src/github.com/c-ollins
git clone https://github.com/c-ollins/btclibwallet $GOPATH/src/github.com/c-ollins/btclibwallet
cd $GOPATH/src/github.com/c-ollins/btclibwallet
export GO111MODULE=on && go mod vendor && export GO111MODULE=off
gomobile bind -target=android/386
cp btclibwallet.aar $BtcandroidDir/app/libs/btclibwallet.aar && cd $BtcandroidDir
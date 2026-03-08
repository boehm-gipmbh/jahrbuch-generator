# Production Deployment Guide
## Native Build and push docker image to registry
``` bash
./mvnw -Pnative,frontend,fly clean package k8s:build k8s:push
```
## Create and deploy to fly.io
``` bash
flyctl deploy --image drdboehm/jahrbuch-generator:fly
```

## Deployment against managed postgresql at fly.io
### Run a proxy connection to postgresql
``` bash
fly mpg proxy -a jahrbuch-generator
```



## Deployment against free-tier plan at fly.io

### Open proxy connection to postgresql
``` bash
fly proxy 5433:5432 -a jahrbuch-generator-pg
```

``` bash
 ngrok http 8080 --url https://elsie-preperusal-overpresumptuously.ngrok-free.dev
```

``` 
flyctl secrets set FREE_DATABASE_URL=postgres://postgres:yKTSyH4aXUsrB9e@jahrbuch-generator-pg.flycast:5432
fly secrets get FREE_DATABASE_URL -a jahrbuch-generator
fly secrets list -a jahrbuch-generator
```
```
/flyctl -a "jahrbuch-generator" ssh console
```
```
ffmpeg -i output-video.mp4 -vf fps=0.5 -q:v 0 output-video-%03d.jpg
 for i in *.jpg; do ffmpeg -i "$i" -vf "transpose=1" -c:a copy "rotated_$i"; done   
```

``` bash
 ./fly-captures-download.sh 
 feh -z -r -F -D10 -Z captures/
```


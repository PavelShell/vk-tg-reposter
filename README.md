How to build image:
docker build -t docker-username/image-name .

How to push image:
docker push docker-username/image-name

Then, at server side install docker and create CRON job:
docker pull sagser/vk-tg-reposter:latest
docker volume create vk-tg-reposter-data
crontab -e
*/5 * * * * docker run --env-file /etc/environment -v vk-tg-reposter-data:/vk-tg-reposter-storage sagser/vk-tg-reposter:latest > /root/logs/vk_tg_reposter_logs_$(date +\%Y\%m\%d_\%H\%M\%S).log 2>&1

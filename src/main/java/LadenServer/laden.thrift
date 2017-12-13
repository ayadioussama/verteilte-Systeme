namespace java com.eviac.blog.samples.thrift.server  // defines the namespace
service StoreService {
    i64 getPrice(1:string articleName)
    i32 orderArticle(1:string articleName, 2:string receiveDate, 3:string customer, 4:i32 amount)
}
